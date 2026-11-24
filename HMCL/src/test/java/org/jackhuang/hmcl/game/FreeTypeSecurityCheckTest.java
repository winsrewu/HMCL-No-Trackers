/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.game;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Tests for [FreeTypeSecurityCheck] parsing and classification.
///
/// The fixtures are synthetic `VERSIONINFO` fragments: a UTF-16LE key followed by a UTF-16LE
/// value, which is exactly what the Windows resource section stores and what the parser reads.
public final class FreeTypeSecurityCheckTest {
    /// Builds a byte array that looks like a `VERSIONINFO` resource fragment.
    ///
    /// @param key   the UTF-8 key, written as UTF-16LE
    /// @param value the UTF-8 value, written as UTF-16LE
    /// @return the synthetic resource bytes
    private static byte[] resource(String key, String value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(key.getBytes(StandardCharsets.UTF_16LE));
        output.writeBytes(new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
        output.writeBytes(value.getBytes(StandardCharsets.UTF_16LE));
        return output.toByteArray();
    }

    /// The version is read from `FileVersion` when it is present.
    @Test
    public void testFileVersion() {
        assertEquals("2.13.2", FreeTypeSecurityCheck.detectVersion(resource("FileVersion", "2.13.2")));
    }

    /// `ProductVersion` is used when `FileVersion` is missing.
    @Test
    public void testProductVersionFallback() {
        assertEquals("2.15.0", FreeTypeSecurityCheck.detectVersion(resource("ProductVersion", "2.15.0")));
    }

    /// Longer version tuples are truncated to the three components used for comparison.
    @Test
    public void testFourComponentVersion() {
        assertEquals("2.15.0", FreeTypeSecurityCheck.detectVersion(resource("FileVersion", "2.15.0.1")));
    }

    /// A resource without any known key is not interpreted.
    @Test
    public void testMissingAnchor() {
        assertNull(FreeTypeSecurityCheck.detectVersion(resource("CompanyName", "FreeType")));
    }

    /// Empty input is not interpreted.
    @Test
    public void testEmptyInput() {
        assertNull(FreeTypeSecurityCheck.detectVersion(new byte[0]));
    }

    /// Builds before 2.15.0 are rejected.
    @Test
    public void testVulnerableVersions() {
        assertEquals(FreeTypeSecurityCheck.Status.VULNERABLE, FreeTypeSecurityCheck.classify("2.13.2"));
        assertEquals(FreeTypeSecurityCheck.Status.VULNERABLE, FreeTypeSecurityCheck.classify("2.14.1"));
        assertEquals(FreeTypeSecurityCheck.Status.VULNERABLE, FreeTypeSecurityCheck.classify("2.14.3"));
        assertEquals(FreeTypeSecurityCheck.Status.VULNERABLE, FreeTypeSecurityCheck.classify("1.9.9"));
    }

    /// 2.15.0 and newer builds are accepted.
    @Test
    public void testSafeVersions() {
        assertEquals(FreeTypeSecurityCheck.Status.SAFE, FreeTypeSecurityCheck.classify("2.15.0"));
        assertEquals(FreeTypeSecurityCheck.Status.SAFE, FreeTypeSecurityCheck.classify("2.15.1"));
        assertEquals(FreeTypeSecurityCheck.Status.SAFE, FreeTypeSecurityCheck.classify("2.15.0.0.1"));
        assertEquals(FreeTypeSecurityCheck.Status.SAFE, FreeTypeSecurityCheck.classify("2.16.0"));
    }
}
