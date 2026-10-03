import org.junit.runner.*;
import org.junit.runner.notification.*;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;

/** Lists the JUnit description tree, then runs individually timed method requests. */
public class JsonRunner {
    private static final PrintStream OUTPUT = System.out;
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Expected test FQCN");
        boolean list = false;
        long timeout = 30000;
        Set<String> excluded = new HashSet<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--list")) list = true;
            else if (args[i].equals("--timeout-ms")) timeout = Long.parseLong(args[++i]);
            else if (args[i].equals("--exclude")) excluded.addAll(Arrays.asList(args[++i].split(",")));
            else throw new IllegalArgumentException("Unknown option: " + args[i]);
        }
        if (timeout <= 0) throw new IllegalArgumentException("Timeout must be positive");
        Class<?> cls = Class.forName(args[0]);
        LinkedHashSet<String> methods = new LinkedHashSet<>();
        collect(Request.aClass(cls).getRunner().getDescription(), methods);
        if (methods.isEmpty()) throw new IllegalStateException("No methods in JUnit description tree");
        if (list) {
            for (String method : methods) OUTPUT.println("{\"method\":" + q(method) + "}");
            return;
        }
        System.setOut(System.err);
        for (String method : methods) {
            if (excluded.contains(method)) continue;
            long start = System.nanoTime();
            ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "test-" + method);
                thread.setDaemon(true);
                return thread;
            });
            Future<Outcome> future = worker.submit(() -> runMethod(cls, method));
            try {
                Outcome outcome = future.get(timeout, TimeUnit.MILLISECONDS);
                emit(method, outcome.status, outcome.message, outcome.exception, start);
            } catch (TimeoutException e) {
                emit(method, "timeout", "Exceeded " + timeout + " ms", null, start);
                OUTPUT.flush();
                // Even unresponsive shutdown hooks must not hide later methods.
                Runtime.getRuntime().halt(3);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                emit(method, cause instanceof AssertionError ? "fail" : "error",
                     String.valueOf(cause.getMessage()), cause.getClass().getName(), start);
            } finally {
                worker.shutdownNow();
            }
        }
        OUTPUT.flush();
        System.exit(0);
    }
    static void collect(Description description, Set<String> methods) {
        if (description.isTest() && description.getMethodName() != null)
            methods.add(description.getMethodName());
        for (Description child : description.getChildren()) collect(child, methods);
    }
    static Outcome runMethod(Class<?> cls, String method) {
        JUnitCore core = new JUnitCore();
        List<Failure> assumptions = new ArrayList<>();
        core.addListener(new RunListener() {
            @Override public void testAssumptionFailure(Failure failure) { assumptions.add(failure); }
        });
        Result result = core.run(Request.method(cls, method));
        if (!result.getFailures().isEmpty()) {
            Failure chosen = result.getFailures().get(0);
            for (Failure failure : result.getFailures()) {
                if (!(failure.getException() instanceof AssertionError)) { chosen = failure; break; }
            }
            Throwable cause = chosen.getException();
            return new Outcome(cause instanceof AssertionError ? "fail" : "error",
                               String.valueOf(chosen.getMessage()), cause.getClass().getName());
        }
        if (!assumptions.isEmpty()) {
            Failure failure = assumptions.get(0);
            return new Outcome("ignored", String.valueOf(failure.getMessage()), failure.getException().getClass().getName());
        }
        if (result.getIgnoreCount() > 0) return new Outcome("ignored", null, null);
        if (result.getRunCount() != 1) return new Outcome("error", "Expected one executed test, got " + result.getRunCount(), "RunnerMethodCount");
        return new Outcome("pass", null, null);
    }
    static class Outcome {
        final String status, message, exception;
        Outcome(String status, String message, String exception) {
            this.status = status; this.message = message; this.exception = exception;
        }
    }
    static void emit(String method, String status, String message, String exception, long start) {
        OUTPUT.println("{\"method\":" + q(method) + ",\"status\":" + q(status)
            + ",\"message\":" + q(message) + ",\"exception\":" + q(exception)
            + ",\"milliseconds\":" + ((System.nanoTime() - start) / 1000000) + "}");
        OUTPUT.flush();
    }
    static String q(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch == '\n') b.append("\\n");
            else if (ch == '\r') b.append("\\r");
            else if (ch == '\t') b.append("\\t");
            else if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
            else b.append(ch);
        }
        return b.append('"').toString();
    }
}
