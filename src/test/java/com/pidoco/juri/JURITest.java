/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Georg Koester, jURI Authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */
package com.pidoco.juri;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.net.InetAddresses;
import org.junit.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;

public class JURITest {

    private JURI cut;

    @Test
    public void testInitilizesEmpty() throws URISyntaxException {
        assertEquals(new URI(""), JURI.createEmpty().getCurrentUri());
    }

    @Test
    public void testParseURIProblemCases() {
        try {
            JURI.parse("//"); // this is recognized as the // in http:// - error in URI impl
            fail();
        } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains("Expected authority"));
        }

        JURI cut = JURI.parse("///"); // the first two / are recognized as the // in http:// - URI impl err
        assertEquals(null, cut.getScheme());
        assertEquals("/", cut.getRawPath());

        try {
            JURI.parse("blah:");
            fail();
        } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains("Expected scheme-specific part"));
        }

        try {
            JURI.parse("http:");
            fail();
        } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains("Expected scheme-specific part"));
        }

    }

    @Test
    public void testIsHavingQueryParameters() throws Exception {
        assertFalse(JURI.createEmpty().isHavingQueryParams());
        assertFalse(JURI.parse("http://domain.org/blah?").isHavingQueryParams());
        assertTrue(JURI.parse("http://domain.org/blah?p").isHavingQueryParams());
        assertTrue(JURI.parse("http://domain.org/blah?p=").isHavingQueryParams());
        assertTrue(JURI.parse("http://domain.org/blah?p=v").isHavingQueryParams());
        assertFalse(JURI.parse("http://domain.org/blah?&").isHavingQueryParams());
    }

    @Test
    public void testSimpleQueryParametersString() throws Exception {
        cut = JURI.create(new URI("/"));
        cut.addQueryParameter("test&1", "ü你&=d");
        cut.addQueryParameter("test&1", "c");
        cut.addQueryParameter("test&1", "c");
        cut.addQueryParameter("test2", "");
        cut.addQueryParameter("test3", "c f");
        cut.addQueryParameter("test4", "c$@-_*?.f");

        assertEquals("/?test%261=%C3%BC%E4%BD%A0%26%3Dd&test%261=c&test%261=c&test2=&test3=c+f&test4=c%24%40-_*%3F.f",
                cut.getCurrentUri().toASCIIString());

        JURI roundtrip = JURI.create(new URI(cut.toString()));
        assertEquals("" + roundtrip.getQueryParameters(), 3, roundtrip.getQueryParameters().get("test&1").size());
        assertTrue(roundtrip.getQueryParameters().get("test&1").contains("ü你&=d"));
        assertEquals("ü你&=d", roundtrip.getQueryParameterFirstValue("test&1"));
        assertTrue(roundtrip.getQueryParameters().get("test&1").contains("c"));
        assertEquals(1, roundtrip.getQueryParameters().get("test2").size());
        assertTrue(roundtrip.getQueryParameters().get("test2").contains(""));
        assertEquals(1, roundtrip.getQueryParameters().get("test3").size());
        assertTrue(roundtrip.getQueryParameters().get("test3").contains("c f"));
        assertEquals(1, roundtrip.getQueryParameters().get("test4").size());
        assertTrue(roundtrip.getQueryParameters().get("test4").contains("c$@-_*?.f"));
    }

    @Test
    public void testEmptyQueryParameters() throws Exception {
        cut = JURI.create(new URI("/"));
        cut.addQueryParameter("test&1", null);
        cut.addQueryParameter("test2", "c");

        assertEquals("/?test%261&test2=c", cut.getCurrentUri().toASCIIString());

        JURI roundtrip = JURI.create(new URI(cut.toString()));
        assertEquals("" + roundtrip.getQueryParameters(), 2, roundtrip.getQueryParameters().size());
        assertTrue(roundtrip.getQueryParameters().containsKey("test&1"));
        assertEquals(1, roundtrip.getQueryParameters().get("test2").size());
        assertTrue(roundtrip.getQueryParameters().get("test2").contains("c"));
    }

    @Test
    public void testReplaceQueryParameterString() throws Exception {
        cut = JURI.create(new URI("/?test2=&test3=c+f&test%261=%C3%BC%E4%BD%A0%26%3Dd&test%261=c"));
        cut.replaceQueryParameter("test2", "df");
        cut.replaceQueryParameter("test3", "33");

        assertEquals("/?test%261=%C3%BC%E4%BD%A0%26%3Dd&test%261=c&test2=df&test3=33",
                cut.getCurrentUri().toASCIIString());
    }

    @Test
    public void testRemoveQueryParameterString() throws Exception {
        cut = JURI.create(new URI("/?test2=&test3=c+f&test%261=%C3%BC%E4%BD%A0%26%3Dd&test%261=c"));
        cut.removeQueryParameter("test2");
        cut.removeQueryParameter("test3");

        assertEquals("/?test%261=%C3%BC%E4%BD%A0%26%3Dd&test%261=c",
                cut.getCurrentUri().toASCIIString());

        cut = JURI.create(new URI("/?test2=&test2=kk&test3=c+f&test%261=%C3%BC%E4%BD%A0%26%3Dd&test%261=c"));
        cut.removeQueryParameter("test2");

        assertEquals("/?test3=c+f&test%261=%C3%BC%E4%BD%A0%26%3Dd&test%261=c",
                cut.getCurrentUri().toASCIIString());
    }

    @Test
    public void testSomePaths() throws Exception {
        cut = JURI.create(new URI("/"));
        assertEquals("", cut.setPath("").getCurrentUri().toASCIIString());
        assertEquals("/", cut.setPath("/").getCurrentUri().toASCIIString());
        assertEquals("/path", cut.setPath("/path").getCurrentUri().toASCIIString());

        assertEquals("/path/", cut.setPath("/path/").getCurrentUri().toASCIIString());
        assertEquals("/path//", cut.setPath("/path//").getCurrentUri().toASCIIString());
        assertEquals("/path/p43", cut.setPath("/path/p43").getCurrentUri().toASCIIString());
        assertEquals("/path/p43/", cut.setPath("/path/p43/").getCurrentUri().toASCIIString());
        assertEquals("/path///p43/", cut.setPath("/path///p43/").getCurrentUri().toASCIIString());

        assertEquals("/path/../p43", cut.setPath("/path/../p43").getCurrentUri().toASCIIString());
        assertEquals("path/p43", cut.setPath("path/p43").getCurrentUri().toASCIIString());
        assertEquals("../path/../p43", cut.setPath("../path/../p43").getCurrentUri().toASCIIString());

        assertEquals("/path:p2", cut.setPath("/path:p2").getCurrentUri().toASCIIString());
        assertEquals("/path%20p2", cut.setPath("/path p2").getCurrentUri().toASCIIString());
        assertEquals("/p%C3%A4th:p2", cut.setPath("/päth:p2").getCurrentUri().toASCIIString());

        assertEquals("/bl%C3%BCb%3Ff%20d=fds;.:f@_$&-~(fd)/", cut.setPath("/blüb?f d=fds;.:f@_$&-~(fd)/").toString());

        assertEquals("", cut.setPath(null).toString());
        cut = JURI.parse("http://dom.org/blah");
        assertEquals("http://dom.org", cut.clone().setPath(null).toString());
    }

    @Test
    public void testConcatRawPaths() throws Exception {
        assertEquals("", JURI.concatRawPaths("", ""));
        assertEquals("/", JURI.concatRawPaths("/", ""));
        assertEquals("/", JURI.concatRawPaths("/", "/"));
        assertEquals("/", JURI.concatRawPaths("", "/"));

        assertEquals("a/b", JURI.concatRawPaths("a", "b"));

        assertEquals("a", JURI.concatRawPaths("", "a"));
        assertEquals("/a", JURI.concatRawPaths("/", "a"));
        assertEquals("/a", JURI.concatRawPaths("/", "/a"));
        assertEquals("/a", JURI.concatRawPaths("", "/a"));
        assertEquals("///a", JURI.concatRawPaths("", "///a"));
        assertEquals("///a", JURI.concatRawPaths("/", "///a"));

        assertEquals("a//", JURI.concatRawPaths("", "a//"));
    }

    @Test
    public void testSetPathSegments() throws CloneNotSupportedException {
        cut = JURI.parse("/");
        assertEquals("http://a//", cut.clone().setScheme("http").setHost("a")
                .setPathSegments(true, true, "").toString());
        assertEquals("", cut.setPathSegments(false, true).toString());
        assertEquals("/", cut.setPathSegments(true, true).toString());
        assertEquals("/", cut.setPathSegments(true, false).toString());
        assertEquals("", cut.setPathSegments(false, false).toString());

        assertEquals("/a/", cut.setPathSegments(true, true, "a").toString());
        assertEquals("a/", cut.setPathSegments(false, true, "a").toString());
        assertEquals("/a", cut.setPathSegments(true, false, "a").toString());
        assertEquals("a", cut.setPathSegments(false, false, "a").toString());
        assertEquals("/%2F/", cut.setPathSegments(true, true, "/").toString());

        assertEquals("/bl%C3%BCb%3Ff%20d=fds;.:f@_$&-~(fd)/bd",
                cut.setPathSegments(true, false, "blüb?f d=fds;.:f@_$&-~(fd)", "bd").toString());
    }

    @Test
    public void testAddPathSegments() throws CloneNotSupportedException {
        cut = JURI.parse("/");
        assertEquals("/a", cut.clone().addPathSegments(false, "a").toString());
        assertEquals("/%2F", cut.clone().addPathSegments(false, "/").toString());

        assertEquals("/a/b", cut.clone().addPathSegments(false, "a", "b").toString());
        assertEquals("/a/b/", cut.clone().addPathSegments(true, "a", "b").toString());

        cut = JURI.parse("");
        assertEquals("a/b/", cut.clone().addPathSegments(true, "a", "b").toString());
        assertEquals("a/b/", cut.clone().addPathSegments(true, "a", "b").toString());

        // on request URI (used internally) normalizes ../ parts if they are preceeded by non-.. parts
        assertEquals("a/b/../", cut.clone().addPathSegments(true, "a", "b", "..").toString());
        assertEquals("a/", cut.clone().addPathSegments(true, "a", "b", "..").getCurrentUri().normalize().toString());
        assertEquals("a/b/..", cut.clone().addPathSegments(false, "a", "b", "..").toString());
        // this shows a situation where normalize leads to a result that might differ from what the caller wanted (no
        // slash at end)
        assertEquals("a/", cut.clone().addPathSegments(false, "a", "b", "..").getCurrentUri().normalize().toString());
    }

    @Test
    public void testEscapeMultiSegmentPath() throws Exception {
        assertEquals("/ad/bc//df///", JURI.escapeMultiSegmentPath("/ad/bc//df///").toString());
        assertEquals("//ad/bc//df///", JURI.escapeMultiSegmentPath("//ad/bc//df///").toString());
        assertEquals("ad/bc//df", JURI.escapeMultiSegmentPath("ad/bc//df").toString());

        assertEquals("ad/b%2Fc/%2F/df", JURI.escapeMultiSegmentPath("ad/b\\/c/\\//df").toString());
    }

    @Test
    public void testGetRawPathSegments() throws Exception {
        assertEquals(Arrays.asList(new String[]{}), JURI.parse("").getRawPathSegments());
        assertEquals(Arrays.asList(new String[]{}), JURI.parse("/").getRawPathSegments());
        assertEquals(Arrays.asList(new String[]{}), JURI.parse("http://a/").getRawPathSegments());

        assertEquals(Arrays.asList(new String[]{""}), JURI.parse("http://a//").getRawPathSegments());
        assertEquals(Arrays.asList(new String[]{"", ""}), JURI.parse("http://a///").getRawPathSegments());

        assertEquals(Arrays.asList(new String[]{"ad", "bc"}), JURI.parse("/ad/bc/").getRawPathSegments());
        assertEquals(Arrays.asList(new String[]{"ad", "bc"}), JURI.parse("ad/bc").getRawPathSegments());

        assertEquals(Arrays.asList(new String[]{"", "", "ad", "", "bc"}),
                JURI.parse("http://a///ad//bc").getRawPathSegments());
        assertEquals(Arrays.asList(new String[]{"ad", "", "bc", ""}),
                JURI.parse("/ad//bc//").getRawPathSegments());
        assertEquals(Arrays.asList(new String[]{"ad", "", "bc", "", ""}),
                JURI.parse("/ad//bc///").getRawPathSegments());
        assertEquals(Arrays.asList(new String[]{"ad", "", "bc", "", "..", ""}),
                JURI.parse("/ad//bc//..//").getRawPathSegments());

        assertEquals(Arrays.asList(new String[]{"ad", "b%2Fc" }),
                JURI.parse("/ad/b%2Fc/").getRawPathSegments());
    }

    @Test
    public void testGetPathSegments() throws Exception {
        assertEquals(Arrays.asList(new String[]{}), JURI.parse("").getPathSegments());
        assertEquals(Arrays.asList(new String[]{}), JURI.parse("/").getPathSegments()); 

        assertEquals(Arrays.asList(new String[]{""}), JURI.parse("http://a//").getPathSegments());
        assertEquals(Arrays.asList(new String[]{"", ""}), JURI.parse("http://a///").getPathSegments());

        assertEquals(Arrays.asList(new String[]{"ad", "bc"}), JURI.parse("/ad/bc/").getPathSegments());
        assertEquals(Arrays.asList(new String[]{"ad", "bc"}), JURI.parse("ad/bc").getPathSegments());
        assertEquals(Arrays.asList(new String[]{"..", "bc"}), JURI.parse("../bc").getPathSegments());

        assertEquals(Arrays.asList(new String[]{"", "", "ad", "", "bc"}),
                JURI.parse("http://a///ad//bc").getPathSegments());
        assertEquals(Arrays.asList(new String[]{"ad", "", "bc", ""}),
                JURI.parse("/ad//bc//").getPathSegments());
        assertEquals(Arrays.asList(new String[]{"ad", "", "bc", "", ""}),
                JURI.parse("/ad//bc///").getPathSegments());
        assertEquals(Arrays.asList(new String[]{"ad", "", "bc", "", "..", ""}),
                JURI.parse("/ad//bc//..//").getPathSegments());

        assertEquals(Arrays.asList(new String[]{"ad", "b/c" }),
                JURI.parse("/ad/b%2Fc/").getPathSegments());
    }

    @Test
    public void testGetRawPath() {
        assertEquals("a/df", JURI.parse("a/df").getRawPath());
        assertEquals("a/bc%20df", JURI.parse("a/bc%20df").getRawPath());
    }

    @Test
    public void testGetPath() {
        assertEquals("a/df", JURI.parse("a/df").getPath());
        assertEquals("a/bc df", JURI.parse("a/bc%20df").getPath());
    }
    
    @Test
    public void testIsHavingPath() throws Exception {
        assertFalse(JURI.parse("").isHavingPath());
        assertTrue(JURI.parse("/").isHavingPath());
        assertTrue(JURI.parse("a/df").isHavingPath());

        assertTrue(JURI.parse("http://blah.de:23/a").isHavingPath());
        assertFalse(JURI.parse("http://blah.de:23").isHavingPath());
        assertFalse(JURI.parse("http://blah.de:23#").isHavingPath());
        assertFalse(JURI.parse("http://blah.de:23?p=x").isHavingPath());

        assertTrue(JURI.parse("http://blah.de:23").setPathSegments(true, false, "blub").isHavingPath());
    }

    @Test
    public void testIsAbsolute() throws Exception {
        assertFalse(JURI.parse("").isPathAbsolute());
        assertTrue(JURI.parse("/").isPathAbsolute());
        assertFalse(JURI.parse("a/df").isPathAbsolute());

        assertTrue(JURI.parse("http://blah.de:23/a").isPathAbsolute());
        assertFalse(JURI.parse("../dsfd/").isPathAbsolute());
    }

    @Test
    public void testIsRelative() throws Exception {
        assertFalse(JURI.parse("").isPathRelative());
        assertFalse(JURI.parse("/").isPathRelative());

        assertFalse(JURI.parse("http://blah.de:23/a").isPathRelative());
        assertTrue(JURI.parse("../dsfd/").isPathRelative());
    }

    @Test
    public void testIsRelativeWeird() throws Exception {
        assertFalse(JURI.parse("http://../dsfd/").isPathRelative());

        cut = JURI.parse("http://[::1.1.1.1]");
        // constructing erroneous path, working until resolved to URI:
        assertTrue(cut.setPath("dsfd/").isPathRelative());
        try {
            cut.getCurrentUri();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Expected port number"));
        }
    }

    @Test
    public void testRemoveUserInfo() throws Exception {
        cut = JURI.create(new URI("http://user:blah@somehost.de/path?aquery=answer#fragging"));

        assertEquals("http://somehost.de/path?aquery=answer#fragging",
                cut.clone().removeUserInfo().toString());
    }

    @Test
    public void testUserInfo() throws Exception {
        cut = JURI.create(new URI("http://somehost.de/path?aquery=answer#fragging"));

        assertEquals("http://blah:blub@somehost.de/path?aquery=answer#fragging",
                cut.clone().setUserInfo("blah", "blub").toString());

        assertEquals("http://blah:@somehost.de/path?aquery=answer#fragging",
                cut.clone().setUserInfo("blah", "").toString());
        assertEquals("http://blah@somehost.de/path?aquery=answer#fragging",
                cut.clone().setUserInfo("blah", null).toString());
    }

    @Test
    public void testGetUser() throws Exception {
        assertEquals("blah",
                JURI.create(new URI("http://blah:blub@somehost.de/path?aquery=answer#fragging")).getUser());

        assertEquals(":blub",
                new URI("http://:blub@somehost.de/path?aquery=answer#fragging").getUserInfo());
        assertEquals("",
                JURI.create(new URI("http://:blub@somehost.de/path?aquery=answer#fragging")).getUser());

        assertEquals("blah",
                JURI.create(new URI("http://blah@somehost.de/path?aquery=answer#fragging")).getUser());
        assertEquals("bl h",
                JURI.create(new URI("http://bl%20h@somehost.de/path?aquery=answer#fragging")).getUser());
        assertEquals(null,
                JURI.create(new URI("http://somehost.de/path?aquery=answer#fragging")).getUser());
        assertEquals(null,
                JURI.create(new URI("http://@somehost.de/path?aquery=answer#fragging")).getUser());
    }

    @Test
    public void testGetPassword() throws Exception {
        assertEquals("blub",
                JURI.create(new URI("http://blah:blub@somehost.de/path?aquery=answer#fragging")).getPassword());
        assertEquals("blub",
                JURI.create(new URI("http://:blub@somehost.de/path?aquery=answer#fragging")).getPassword());
        assertEquals(null,
                JURI.create(new URI("http://blah@somehost.de/path?aquery=answer#fragging")).getPassword());
        assertEquals("blüb",
                JURI.create(new URI("http://blah:bl%C3%BCb@somehost.de/path?aquery=answer#fragging")).getPassword());
        assertEquals(null,
                JURI.create(new URI("http://somehost.de/path?aquery=answer#fragging")).getPassword());
        assertEquals(null,
                JURI.create(new URI("http://@somehost.de/path?aquery=answer#fragging")).getPassword());
    }

    @Test
    public void testSchemeReplacement() throws Exception {
        cut = JURI.create(new URI("http://somehost.de/path?aquery=answer#fragging"));

        assertEquals("https://somehost.de/path?aquery=answer#fragging",
                cut.clone().setScheme("https").toString());

        assertEquals("jk23s://somehost.de/path?aquery=answer#fragging",
                cut.clone().setScheme("jk23s").toString());

        assertEquals("//somehost.de/path?aquery=answer#fragging",
                cut.clone().setScheme("").toString());
    }

    @Test
    public void testRemoveAuthority() throws Exception {
        cut = JURI.create(new URI("http://somehost.de/path?aquery=answer#fragging"));

        assertEquals("/path?aquery=answer#fragging",
                cut.clone().removeAuthorityAndScheme().toString());
    }

    @Test
    public void testHostReplacement() throws Exception {
        cut = JURI.create(new URI("https://somehost.de/path?aquery=answer#fragging"));

        assertEquals("https://otherhost.com/path?aquery=answer#fragging",
                cut.clone().setHost("otherhost.com").getCurrentUri().toASCIIString());

        assertEquals("https://k%C3%B6ster.com/path?aquery=answer#fragging",
                cut.clone().setHost("köster.com").getCurrentUri().toASCIIString());

        assertEquals("https:/path?aquery=answer#fragging",
                cut.clone().setHost("").toString());
    }

    @Test
    public void checkIfAddressHasHostnameWithoutNameLookup() throws UnknownHostException {
        byte[] simpleAddress = new byte[]{1, 1, 1, 1};
        assertFalse(JURI.checkIfAddressHasHostnameWithoutNameLookup(InetAddress.getByAddress(simpleAddress)));
        assertTrue(JURI.checkIfAddressHasHostnameWithoutNameLookup(InetAddress.getByAddress(
                "somehost.de", simpleAddress)));

        assertFalse(JURI.checkIfAddressHasHostnameWithoutNameLookup(
                InetAddresses.forString("1080:0:0:0:8:800:200C:4171")));
        assertTrue(JURI.checkIfAddressHasHostnameWithoutNameLookup(InetAddress.getByAddress(
                "somehost.de", InetAddresses.forString("1080:0:0:0:8:800:200C:4171").getAddress())));
    }

    @Test
    public void testFragmentReplacement() throws Exception {
        JURI orig = JURI.create(new URI("https://somehost.de/path?aquery=answer#fragging"));

        assertEquals("https://somehost.de/path?aquery=answer", orig.clone().setFragment(null).toString());

        String testFrag = "blah/blüb?f d=fds;:f@_$&-~(fd)";
        cut = orig.clone().setFragment(testFrag);
        cut.getCurrentUri(); // build
        assertEquals(testFrag,
                cut.getFragment());
        assertEquals("https://somehost.de/path?aquery=answer#" + "blah/bl%C3%BCb?f%20d=fds;:f@_$&-~(fd)",
                cut.getCurrentUri().toASCIIString());

    }

    @Test
    public void testPortReplacement() throws Exception {
        cut = JURI.create(new URI("https://somehost.de/path?aquery=answer#fragging"));

        assertEquals("https://somehost.de:81/path?aquery=answer#fragging",
                cut.setPort(81).getCurrentUri().toASCIIString());
    }

    @Test
    public void testWeirdPortNums() throws Exception {
        assertEquals(0, new URI("https://somehost.de:0/path?aquery=answer#fragging").getPort());

        cut = JURI.create(new URI("https://somehost.de:0/path?aquery=answer#fragging"));
        assertEquals(0, cut.getPort());
        cut = JURI.create(new URI("https://somehost.de:-2/path?aquery=answer#fragging"));
        assertEquals(-1, cut.getPort());

        cut = JURI.create(new URI("https://somehost.de:81/path?aquery=answer#fragging"));
        assertEquals("https://somehost.de/path?aquery=answer#fragging",
                cut.setPort(0).getCurrentUri().toASCIIString());

        cut = JURI.create(new URI("https://somehost.de:81/path?aquery=answer#fragging"));
        assertEquals("https://somehost.de/path?aquery=answer#fragging",
                cut.setPort(-2).getCurrentUri().toASCIIString());

        assertEquals(1000000, new URI("https://somehost.de:1000000/path?aquery=answer#fragging").getPort());

        cut = JURI.create(new URI("https://somehost.de:81/path?aquery=answer#fragging"));
        assertEquals("https://somehost.de:1000000/path?aquery=answer#fragging",
                cut.setPort(1000000).getCurrentUri().toASCIIString());
    }

    @Test
    public void testHostReplacementIpv6() throws Exception {
        JURI orig = JURI.create(new URI("https://somehost.de:81/path?aquery=answer#fragging"));

        // ipv4 simple (just to show)
        assertEquals("https://192.168.2.2:81/path?aquery=answer#fragging",
                orig.clone().setHost("192.168.2.2").getCurrentUri().toASCIIString());

        // ipv4 as ipv6
        assertEquals("https://[::192.168.2.2]:81/path?aquery=answer#fragging",
                orig.clone().setHost("[::192.168.2.2]").getCurrentUri().toASCIIString());

        assertEquals("https://[1080:0:0:0:8:800:200C:4171]:81/path?aquery=answer#fragging",
                orig.clone().setHost("[1080:0:0:0:8:800:200C:4171]").getCurrentUri().toASCIIString());

        assertEquals("https://[1080::8:800:200C:4171]:81/path?aquery=answer#fragging",
                orig.clone().setHost("[1080::8:800:200C:4171]").getCurrentUri().toASCIIString());

        assertEquals("https://[::FFFF:129.144.52.38]:81/path?aquery=answer#fragging",
                orig.clone().setHost("[::FFFF:129.144.52.38]").getCurrentUri().toASCIIString());

        assertEquals("https://[::FFFF:129.144.52.38]:81/path?aquery=answer#fragging",
                orig.clone().setHost("[::FFFF:129.144.52.38]").getCurrentUri().toASCIIString());
    }

    @Test
    public void testSetSchemeSpecificPart() {
        cut = JURI.parse("");

        assertEquals("mailto:pel%C3%A9@domain.org",
                cut.clone().setScheme("mailto").setSchemeSpecificPart("pelé@domain.org").toString());

        assertEquals("unspecified:blah",
                cut.clone().setSchemeSpecificPart("blah").toString());

        assertEquals("unspecified:blah/bl%C3%BCb?f%20d=fds;:f@_$&-~(fd)",
                cut.clone().setSchemeSpecificPart("blah/blüb?f d=fds;:f@_$&-~(fd)").toString());

        // test setting something and then replacing the scheme specific part, overriding previous edits:
        cut = JURI.parse("http://dom.org/path/#frag");
        cut.addPathSegments(false, "a");
        cut.setFragment("newFrag");
        cut.setSchemeSpecificPart("//somehost.org/üpath");
        assertEquals("http://somehost.org/%C3%BCpath", cut.toString());

        try {
            assertEquals("http:", JURI.parse("http://dom.org/path").setSchemeSpecificPart("").toString());
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Expected scheme-specific part"));
        }

        try {
            assertEquals("http:", JURI.parse("http://dom.org/path").setRawSchemeSpecificPart("").toString());
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Expected scheme-specific part"));
        }
    }

    @Test
    public void testSetSchemeSpecificPartProblem() {
        cut = JURI.parse("");

        String simplePart = "//dom.org/path";
        cut = JURI.parse("http:" + simplePart);
        cut.addQueryParameter("p", "a value");

        String selfEditOfSchemeSpecifcPart = JURI.createEmpty().setScheme("http").setSchemeSpecificPart(
                simplePart + "?p=a value").toString();
        assertNotEquals("Escaping works differently here",
                selfEditOfSchemeSpecifcPart,
                cut.toString());

        assertEquals("wrong escaping", "http:" + simplePart + "?p=a%20value", selfEditOfSchemeSpecifcPart);

        assertEquals("this would work, but not really helping, and see further for problems",
                JURI.createEmpty().setScheme("http").setSchemeSpecificPart(simplePart + "?p=a+value").toString(),
                cut.toString());

        assertNotEquals("cannot self-escape - double escaping will happen",
                JURI.createEmpty().setScheme("http").setSchemeSpecificPart("//dom.org/%C3%BCpath").toString(),
                JURI.createEmpty().setScheme("http").setHost("dom.org").setPath("/üpath").toString());

        assertEquals("this would work",
                JURI.createEmpty().setScheme("http").setRawSchemeSpecificPart("//dom.org/%C3%BCpath").toString(),
                JURI.createEmpty().setScheme("http").setHost("dom.org").setPath("/üpath").toString());
    }

    @Test
    public void testGetSchemeSpecificPart() {
        cut = JURI.parse("");

        assertEquals("pelé@domain.org",
                JURI.parse("mailto:pel%C3%A9@domain.org").getSchemeSpecificPart());

        assertEquals("pelé@domain.org",
                JURI.parse("blah:pel%C3%A9@domain.org").getSchemeSpecificPart());
    }

    @Test
    public void testGetScheme() {

        assertEquals("mailto",
                JURI.parse("mailto:pel%C3%A9@domain.org").getScheme());

        assertEquals("blah",
                JURI.parse("blah:something").getScheme());

        assertEquals("blah",
                JURI.parse("blah:a").getScheme());

        assertNull(
                JURI.parse("blah").getScheme());
    }
    
    @Test
    public void testNavigate() {
        cut = JURI.parse("/a/b/c/d/e/f");
        assertEquals("http://www.google.com/search?q=2", cut.clone().navigate("http://www.google.com/search?q=2").toString());
        assertEquals("/a/b.html", cut.clone().navigate("/a/b.html").getPath());
        assertEquals("/a/b/c/d/e/g.html", cut.clone().navigate( "g.html").getPath());
        assertEquals("/a/b/c/c/h", cut.clone().navigate("../../c/h").getPath());
        assertEquals("/a/b/c/d/c.html", cut.clone().navigate("../c.html").getPath());
        assertEquals("/a/b/c/d/c/t/m.xml?q=1", cut.clone().navigate("../c/t/q/../m.xml?q=1").toString());
        assertEquals("/a/b/c/d/e/f#anchor", cut.clone().navigate("#anchor").toString());
        assertEquals("/a/b/c/d/e/f", cut.clone().navigate("#").toString());
        assertEquals("/a/b.html#anchor", cut.clone().navigate("/a/b.html#anchor").toString());

        cut = JURI.parse("http://example.com//a/b/c/d/e/f");
        assertEquals("http://www.google.com/search?q=2", cut.clone().navigate("http://www.google.com/search?q=2").toString());
        assertEquals("http://example.com/a/b.html", cut.clone().navigate("/a/b.html").toString());
        assertEquals("http://example.com/a/b/c/d/e/g.html", cut.clone().navigate( "g.html").toString());
        assertEquals("http://example.com/a/b/c/c/h", cut.clone().navigate("../../c/h").toString());
        assertEquals("http://example.com/a/b/c/d/c.html", cut.clone().navigate("../c.html").toString());
        assertEquals("http://example.com/a/b/c/d/c/t/m.xml?q=1", cut.clone().navigate("../c/t/q/../m.xml?q=1").toString());
        assertEquals("http://example.com//a/b/c/d/e/f#anchor", cut.clone().navigate("#anchor").toString());
        assertEquals("http://example.com/a/b.html#anchor", cut.clone().navigate("/a/b.html#anchor").toString());

        cut = JURI.parse("http://example.com");
        assertEquals("http://www.google.com/search?q=2", cut.clone().navigate("http://www.google.com/search?q=2").toString());
        assertEquals("http://example.com/a/b.html", cut.clone().navigate("/a/b.html").toString());
        assertEquals("http://example.com/g.html", cut.clone().navigate( "g.html").toString());
        assertEquals("http://example.com/../../c/h", cut.clone().navigate("../../c/h").toString());
        assertEquals("http://example.com/../c.html", cut.clone().navigate("../c.html").toString());
        assertEquals("http://example.com/../c/t/m.xml?q=1", cut.clone().navigate("../c/t/q/../m.xml?q=1").toString());
        assertEquals("http://example.com#anchor", cut.clone().navigate("#anchor").toString());
        assertEquals("http://example.com/a/b.html#anchor", cut.clone().navigate("/a/b.html#anchor").toString());

        cut = JURI.parse("http://example.com?c=d#asdf");
        assertEquals("http://www.google.com/search?q=2", cut.clone().navigate("http://www.google.com/search?q=2").toString());
        assertEquals("http://example.com/a/b.html", cut.clone().navigate("/a/b.html").toString());
        assertEquals("http://example.com/g.html", cut.clone().navigate( "g.html").toString());
        assertEquals("http://example.com/../../c/h", cut.clone().navigate("../../c/h").toString());
        assertEquals("http://example.com/../c.html", cut.clone().navigate("../c.html").toString());
        assertEquals("http://example.com/../c/t/m.xml?q=1", cut.clone().navigate("../c/t/q/../m.xml?q=1").toString());
        assertEquals("http://example.com?c=d#anchor", cut.clone().navigate("#anchor").toString());
        assertEquals("http://example.com/a/b.html#anchor", cut.clone().navigate("/a/b.html#anchor").toString());
    }
}
