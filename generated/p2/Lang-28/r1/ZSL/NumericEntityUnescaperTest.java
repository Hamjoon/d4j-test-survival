/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.lang3.text.translate;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;

/**
 * Comprehensive JUnit‑4 test suite for {@link NumericEntityUnescaper}.
 *
 * <p>The tests cover:
 * <ul>
 *   <li>Decimal numeric entities</li>
 *   <li>Hexadecimal numeric entities (both upper‑ and lower‑case “x”)</li>
 *   <li>Invalid numeric values (NumberFormatException handling)</li>
 *   <li>Input that does not start with a numeric entity</li>
 *   <li>Entities that appear at an offset inside a larger string</li>
 *   <li>Behaviour when the terminating semicolon is missing (expected
 *       {@link StringIndexOutOfBoundsException})</li>
 *   <li>Correct return value – the number of characters consumed</li>
 *   <li>Correct data written to the supplied {@link java.io.Writer}</li>
 * </ul>
 * </p>
 *
 * @author OpenAI ChatGPT
 */
public class NumericEntityUnescaperTest {

    private NumericEntityUnescaper unescaper;

    @Before
    public void setUp() {
        unescaper = new NumericEntityUnescaper();
    }

    /**
     * Helper that invokes {@link NumericEntityUnescaper#translate(CharSequence, int, java.io.Writer)}
     * and returns the number of characters consumed.
     */
    private int translate(String input, int index, StringWriter out) throws IOException {
        return unescaper.translate(input, index, out);
    }

    @Test
    public void testDecimalEntity() throws IOException {
        String input = "&#65;"; // 65 == 'A'
        StringWriter out = new StringWriter();

        int consumed = translate(input, 0, out);

        assertEquals("Consumed characters should equal input length", input.length(), consumed);
        assertEquals("Writer should contain the decoded character", "A", out.toString());
    }

    @Test
    public void testHexEntityUppercaseX() throws IOException {
        String input = "&#X41;"; // 0x41 == 'A'
        StringWriter out = new StringWriter();

        int consumed = translate(input, 0, out);

        assertEquals("Upper‑case X should be recognised as hex", input.length(), consumed);
        assertEquals("Decoded character should be 'A'", "A", out.toString());
    }

    @Test
    public void testHexEntityLowercaseX() throws IOException {
        String input = "&#x41;"; // 0x41 == 'A'
        StringWriter out = new StringWriter();

        int consumed = translate(input, 0, out);

        assertEquals("Lower‑case x should be recognised as hex", input.length(), consumed);
        assertEquals("Decoded character should be 'A'", "A", out.toString());
    }

    @Test
    public void testHexEntityWithLeadingZeros() throws IOException {
        String input = "&#x000A;"; // newline (0x0A)
        StringWriter out = new StringWriter();

        int consumed = translate(input, 0, out);

        assertEquals(input.length(), consumed);
        assertEquals("\n", out.toString());
    }

    @Test
    public void testZeroEntity() throws IOException {
        String input = "&#0;"; // NUL character
        StringWriter out = new StringWriter();

        int consumed = translate(input, 0, out);

        assertEquals(input.length(), consumed);
        assertEquals("\u0000", out.toString());
    }

    @Test
    public void testMaximumBmpEntity() throws IOException {
        String input = "&#65535;"; // 0xFFFF
        StringWriter out = new StringWriter();

        int consumed = translate(input, 0, out);

        assertEquals(input.length(), consumed);
        assertEquals("\uFFFF", out.toString());
    }

    @Test
    public void testInvalidNumberFormatReturnsZero() throws IOException {
        String input = "&#xG;"; // 'G' is not a valid hex digit
        StringWriter out = new StringWriter();

        int consumed = translate(input, 0, out);

        assertEquals("Invalid numeric value must result in 0 characters consumed", 0, consumed);
        assertEquals("Writer must remain empty on format error", "", out.toString());
    }

    @Test
    public void testNonEntityReturnsZero() throws IOException {
        String input = "Hello World";
        StringWriter out = new StringWriter();

        int consumed = translate(input, 0, out);

        assertEquals("Non‑entity input should not be consumed", 0, consumed);
        assertEquals("Writer must stay untouched", "", out.toString());
    }

    @Test
    public void testEntityAtNonZeroOffset() throws IOException {
        String input = "foo&#66;bar"; // &#66; == 'B'
        StringWriter out = new StringWriter();

        int startIndex = 3; // position of '&'
        int consumed = translate(input, startIndex, out);

        assertEquals("Consumed length must match the entity length", 5, consumed);
        assertEquals("Writer should contain decoded character only", "B", out.toString());

        // Verify that surrounding characters are untouched in the original string
        assertEquals("foo&#66;bar", input);
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testMissingSemicolonThrowsException() throws IOException {
        // The implementation does not guard against a missing ';' and will run off the
        // end of the CharSequence, resulting in a StringIndexOutOfBoundsException.
        String input = "&#123"; // No terminating semicolon
        StringWriter out = new StringWriter();

        // The call is expected to throw
        translate(input, 0, out);
    }

    @Test
    public void testPartialMatchDoesNotConsume() throws IOException {
        // The first character is '&' but the second is not '#', so the method must return 0.
        String input = "&amp;"; // Not a numeric entity
        StringWriter out = new StringWriter();

        int consumed = translate(input, 0, out);

        assertEquals("Partial match (missing '#') must return 0", 0, consumed);
        assertEquals("Writer must stay empty", "", out.toString());
    }
}
