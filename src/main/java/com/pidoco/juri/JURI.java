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

import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.net.InetAddresses;
import com.google.common.net.UrlEscapers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helps especially if you have to deal with changing (or even only getting) query parameters.
 *
 * <p>
 * The class doesn't verify the URI until {@link #getCurrentUri()} or any of the methods calling it like
 * {@link #toString()} are called. E.g.
 * <pre>
 *     cut = JURI.parse("http://[::1.1.1.1]")
 *     assertTrue(cut.setPath("dsfd/").isPathRelative());
 *     cut.getCurrentUri(); // fails here
 * </pre>
 * </p>
 *
 * <p>This is a mutable class - so it doesn't provide an equals or hashCode implementation. Use URI from
 * {@link #getCurrentUri()} or String from {@link #toString()} if you need to compare or store in a map.
 * It doesn't provide an equals or hashCode implementation so as to fail early when it is used in this anti-pattern.
 * </p>
 *
 * Why not {@link java.net.URL} as the underlying class? Its equals calls DNS and compares IP addresses....
 */
@ParametersAreNonnullByDefault
public class JURI implements Cloneable {

    private static final Logger LOG = LoggerFactory.getLogger(JURI.class);

    public static final URI EMPTY_URI;
    static {
        URI protoEmptyUri = null;
        try {
            protoEmptyUri = new URI("");
        } catch (URISyntaxException e) {
            e.printStackTrace(); // shouldn't happen
        }
        EMPTY_URI = protoEmptyUri;
    }

    private URI prototype;

    private URI currentUri;

    private boolean changeUnderway = false;

    private Multimap<String, String> currentQueryParameters;

    private String scheme;
    private boolean removeAuthorityAndScheme = false;
    private String rawUserInfo;
    private String host;
    private Integer port ;
    private String rawPath;
    private String fragment;

    /**
     * Creates a URI with empty content "".
     */
    private JURI() {
        prototype = EMPTY_URI;
    }

    /**
     * Init to a URI of '' (empty string).
     */
    public static JURI createEmpty() {
        return new JURI();
    }

    /**
     * @throws IllegalArgumentException if the given URI cannot be parsed.
     */
    public static JURI parse(String rawURI) {
        JURI result = new JURI();
        result.parseNew(rawURI);
        return result;
    }

    private JURI parseNew(String rawURI) {
        try {
            this.prototype = this.currentUri = new URI(rawURI);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    String.format("Cannot parse as URI: '%s'. Reason: %s", rawURI, e.getMessage()), e);
        }
        return this;
    }

    private JURI resetBlank() {
        prototype = null;
        currentUri = null;
        changeUnderway = false;
        currentQueryParameters = null;
        scheme = null;
        removeAuthorityAndScheme = false;
        rawUserInfo = null;
        host = null;
        port = null;
        rawPath = null;
        fragment = null;
        return this;
    }

    public static JURI create(URI uri) {
        JURI result = new JURI();
        result.prototype = result.currentUri = uri;
        return result;
    }

    /**
     * Recreates the URI if changed, should not be used while changing it.
     */
    public URI getCurrentUri() {
        if (currentUri == null) {
            try {
                buildAndReset();
            } catch (URISyntaxException use) {
                throw new IllegalStateException(use);
            }
        }
        return currentUri;
    }

    public void buildAndReset() throws URISyntaxException {
        currentUri = new URI(buildNoSideEffects().toString());

        reset();
    }

    protected CharSequence buildNoSideEffects() throws URISyntaxException {
        String scheme = this.scheme == null ? prototype.getScheme() : this.scheme;
        String rawUserInfo = this.rawUserInfo == null ? prototype.getRawUserInfo() : this.rawUserInfo;
        CharSequence rawHost = this.host == null ? prototype.getHost() : this.buildHostString();
        int port = this.port == null ? this.prototype.getPort() : this.port;
        CharSequence rawPath = this.rawPath == null ? this.prototype.getRawPath() : this.rawPath;
        CharSequence rawQuery = this.buildQueryParametersString();
        String rawFragment = this.fragment == null ? this.prototype.getRawFragment() :
                UrlEscapers.urlFragmentEscaper().escape(this.fragment);

        StringBuilder builder = new StringBuilder(32);
        if (!removeAuthorityAndScheme) {
            if (StringUtils.isNotBlank(scheme)) {
                builder.append(scheme);
                builder.append(':');
            }

            boolean hasAuthority = StringUtils.isNotBlank(rawHost) || port > -1 || StringUtils.isNotBlank(rawUserInfo);
            if (hasAuthority) {
                builder.append("//");
            }
            if (StringUtils.isNotBlank(rawUserInfo)) {
                builder.append(rawUserInfo).append('@');
            }
            if (StringUtils.isNotBlank(rawHost)) {
                builder.append(rawHost);
            }
            if (port > 0) {
                builder.append(':').append(port);
            }
        }

        if (StringUtils.isNotBlank(rawPath)) {
            builder.append(rawPath);
        }
        if (StringUtils.isNotBlank(rawQuery)) {
            builder.append('?').append(rawQuery);
        }
        if (StringUtils.isNotBlank(rawFragment)) {
            builder.append('#').append(rawFragment);
        }

        return builder;
    }

    @Nullable
    private CharSequence buildHostString() {
        String decodedHost = this.host;
        if (decodedHost == null) {
            decodedHost = prototype.getHost();
        }
        if (StringUtils.startsWith(decodedHost, "[") && StringUtils.endsWith(decodedHost, "]")) {
            return decodedHost; // ipv6 and other address literal that is not encoded (at least currently)
        }
        return urlEncode(decodedHost);
    }

    private void reset() {
        if (currentUri != null) {
            prototype = currentUri;
        }

        currentQueryParameters = null;

        scheme = null;
        removeAuthorityAndScheme = false;
        rawUserInfo = null;
        host = null;
        port = null;
        rawPath = null;
        fragment = null;
    }

    /**
     * @param scheme null not supported
     */
    public JURI setScheme(String scheme) {
        startChange();

        this.removeAuthorityAndScheme = false;
        this.scheme = scheme;

        changed();
        return this;
    }

    @Nullable
    public String getScheme() {
        if (removeAuthorityAndScheme) {
            return null;
        }
        if (scheme == null) {
            scheme = prototype.getScheme();
        }
        return scheme;
    }

    /**
     * Use {@link #urlEncode(String)} if you want to encode the scheme specific part, or the http-specific manipulation
     * methods provided by this class.
     *
     * <p>If no scheme is currently set the scheme will become 'unspecified'.</p>
     *
     * @param rawSchemeSpecificPart null or "" not allowed.
     */
    public JURI setRawSchemeSpecificPart(String rawSchemeSpecificPart) {
        rawSchemeSpecificPart = StringUtils.defaultString(rawSchemeSpecificPart);

        String newUri;
        if (StringUtils.isBlank(getScheme())) {
            newUri = "unspecified:" + rawSchemeSpecificPart;
        } else {
            newUri = getScheme() + ":" + rawSchemeSpecificPart;
        }

        URI newUriObj;
        try {
            newUriObj = new URI(newUri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        currentUri = newUriObj;
        reset();
        return this;
    }

    /**
     * @return maybe ""
     */
    public String getRawSchemeSpecificPart() {
        return  getCurrentUri().getRawSchemeSpecificPart();
    }

    /**
     * BEWARE, this cannot be used to escape many http scheme specific parts, as possibly other scheme's specific parts.
     * Problem is the different escaping of some characters depending on the (semantic) location.
     *
     * <p>If no scheme is currently set the scheme will become 'unspecified'.</p>
     *
     * @param schemeSpecificPart null or "" not allowed.
     */
    public JURI setSchemeSpecificPart(String schemeSpecificPart) {
        schemeSpecificPart = StringUtils.defaultString(schemeSpecificPart);

        setRawSchemeSpecificPart(UrlEscapers.urlFragmentEscaper().escape(schemeSpecificPart));
        return this;
    }

    /**
     * Return e.g. the decoded mail address for URI <code>mailto:Pelé@domain.org</code>.
     * @return maybe ""
     */
    public String getSchemeSpecificPart() {
        return getCurrentUri().getSchemeSpecificPart();
    }

    public JURI removeAuthorityAndScheme() {
        startChange();

        removeAuthorityAndScheme = true;

        changed();
        return this;
    }

    public JURI setUserInfo(String user, @Nullable String pw) {
        startChange();

        if (StringUtils.isBlank(user)) {
            this.rawUserInfo = "";
        } else {
            this.removeAuthorityAndScheme = false;
            this.rawUserInfo = urlEncode(user);

            if (pw != null) {
                this.rawUserInfo = this.rawUserInfo + ":" + urlEncode(pw);
            }
        }

        changed();
        return this;
    }

    @Nullable
    public String getRawUserInfo() {
        if (removeAuthorityAndScheme) {
            return null;
        }
        if (rawUserInfo == null) {
            rawUserInfo = prototype.getRawUserInfo();
        }
        return rawUserInfo;
    }

    @Nullable
    public String getUser() {
        String raw = getRawUserInfo();
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String[] split = StringUtils.splitPreserveAllTokens(raw, ':');
        if (split.length > 0) {
            return urlDecode(split[0]);
        }
        return null;
    }

    @Nullable
    public String getPassword() {
        String raw = getRawUserInfo();
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String[] split = StringUtils.splitPreserveAllTokens(raw, ':');
        if (split.length > 1) {
            return urlDecode(split[1]);
        }
        return null;
    }

    public JURI removeUserInfo() {
        setUserInfo("", "");
        return this;
    }

    /**
     * @param host null or "" remove host.
     */
    public JURI setHost(@Nullable String host) {
        startChange();

        this.host = host;
        this.removeAuthorityAndScheme = false;

        changed();
        return this;
    }

    /**
     * Sets the host to the given numeric ip address.
     *
     */
    public JURI setHost(@Nullable InetAddress address) {
        return setHost(address, false);
    }

    /**
     * Does not attempt name resolution (and therefore does not block).
     *
     * @param address to set as hostname. Works with both ipv4 and ipv6 addresses. ipv4 addresses in ipv6 form are
     *                set as ipv6 address. A given ipv4 address is not converted into an ipv6 address.
     * @param useHostIfAvailable if true, checks if the address has a known hostname
     *                           and uses that name instead of the address. Consider using {@link #setHost(String)}}
     *                           directly instead.
     *                           No name resolution is performed at the penalty of additional string concatenation
     *                           when using {@link InetAddress#toString()}.
     */
    public JURI setHost(@Nullable InetAddress address, boolean useHostIfAvailable) {
        if (address == null) {
            return setHost("");
        }

        if (useHostIfAvailable && checkIfAddressHasHostnameWithoutNameLookup(address)) {
            this.setHost(address.getHostName());
        } else {
            this.setHost(InetAddresses.toUriString(address));
        }
        return this;
    }

    /**
     * Relies on implementation details of the InetAddress class, hopefully not changing. There is a test, of course.
     */
    protected static boolean checkIfAddressHasHostnameWithoutNameLookup(InetAddress address) {
        // Cannot check with getHostName or getCanonicalName because they can incur a name lookup and we want to avoid
        // blocking.
        // Remark: InetSocketAddress's unresolved and holder functionality not better suited.
        return !address.toString().startsWith("/");
    }

    @Nullable
    public String getHost() {
        if (removeAuthorityAndScheme) {
            return null;
        }
        if (host == null) {
            host = prototype.getHost();
        }
        return host;
    }

    /**
     *
     * @param port null or -1 remove
     */
    public JURI setPort(@Nullable Integer port) {
        startChange();

        if (port == null || port <= 0) {
            this.port = Integer.valueOf(-1);
        } else {
            this.port = port;
        }

        changed();
        return this;
    }

    public int getPort() {
        if (removeAuthorityAndScheme) {
            return -1;
        }
        if (port == null) {
            return prototype.getPort();
        }
        return port;
    }

    public boolean isHavingPort() {
        return getPort() > 0;
    }

    /**
     * Replace current path with the concatenation of the given segments.
     *
     * @param absolute (not relative) if true will set the path with '/' at
     *                 beginning. Also if no segments are given.
     * @param slashAtEnd if true and if there is at least one segment, will add a terminating '/' to path.
     * @param segments here even slash ('/') characters can be specified without escaping them - they will be
     *                 escaped to %2F by the method.
     */
    public JURI setPathSegments(boolean absolute, boolean slashAtEnd, String... segments) {
        StringBuilder result = buildRawPathString(absolute, slashAtEnd, segments);

        setRawPath(result.toString());
        return this;
    }

    public StringBuilder buildRawPathString(boolean absolute, boolean slashAtEnd, String[] segments) {
        StringBuilder result = new StringBuilder();
        if (absolute) {
            result.append('/');
        }
        boolean addedOne = false;
        for (String s : segments) {
            result.append(UrlEscapers.urlPathSegmentEscaper().escape(s));
            result.append('/');
            addedOne = true;
        }
        if (addedOne && !slashAtEnd) {
            result.deleteCharAt(result.length() - 1);
        }
        return result;
    }

    public JURI addPathSegments(boolean slashAtEnd, String... segments) {
        if (segments.length < 1) {
            return this;
        }

        StringBuilder toAdd = buildRawPathString(false, slashAtEnd, segments);
        addRawPath(toAdd);
        return this;
    }

    /**
     * <pre>
     *     "".addRawPath("") -> ""
     *     "/".addRawPath("") -> "/"
     *     "".addRawPath("/") -> "/"
     *     "a".addRawPath("") -> "a/"
     *     "a".addRawPath("b") -> "a/b"
     *     "/".addRawPath("/") -> "/"
     * </pre>
     */
    public JURI addRawPath(CharSequence toAdd) {
        String currentRawPath = StringUtils.defaultString(getRawPath());
        setRawPath(concatRawPaths(currentRawPath, toAdd));
        return this;
    }

    /**
     * <pre>
     *     "".addRawPath("") -> ""
     *     "/".addRawPath("") -> "/"
     *     "".addRawPath("/") -> "/"
     *     "a".addRawPath("") -> "a/"
     *     "a".addRawPath("b") -> "a/b"
     *     "/".addRawPath("/") -> "/"
     * </pre>
     */
    public static String concatRawPaths(CharSequence left, CharSequence right) {
        boolean needsSeparator = false;
        boolean rightStartsWithSlash = StringUtils.startsWith(right, "/");
        int rightStart = 0;
        if (left.length() > 0) {
            if (StringUtils.endsWith(left, "/")) {
                if (rightStartsWithSlash) {
                    rightStart = 1;
                }
            } else {
                if (!rightStartsWithSlash) {
                    needsSeparator = true;
                }
            }
        }

        return left + (needsSeparator ? "/" : "")
                + right.subSequence(rightStart, right.length());
    }

    /**
     * @param unescapedPath null or "" clear the path. Path may contain characters that need escaping like umlauts etc.
     *                      Segments are separated by '/'. With this method '/' cannot be escaped, use one of the
     *                      {@link #setPathSegments(boolean, boolean, String...)} if your path segments may contain
     *                      '/' characters, but
     *                      beware that the {@link URI} class does not support that. Path is not
     *             canonicalized (//, ../ resolved etc) until URI is created with e.g. {@link #getCurrentUri()} or
     *             {@link #toString()}, which uses {@link #getCurrentUri()}.
     */
    public JURI setPath(@Nullable String unescapedPath) {
        startChange();

        if (unescapedPath == null) {
            unescapedPath = "";
        }
        setRawPath(escapeMultiSegmentPath(unescapedPath).toString());

        changed();
        return this;
    }

    /**
     * Escapes non-path characters. Slash ('/') characters may be included in segments if escaped by backslash.
     * @param completeUnescapedPath e.g. //b\/f//kf/ -> //b%XXf//kf/
     * @return the used string builder.
     */
    public static StringBuilder escapeMultiSegmentPath(String completeUnescapedPath) {
        StringBuilder pathBuilder = new StringBuilder();

        StringBuilder temp = new StringBuilder();

        for (int i = 0; i < completeUnescapedPath.length(); i++) {
            char current = completeUnescapedPath.charAt(i);
            if (current == '\\') {
                if (isSlashAtPos(completeUnescapedPath, i + 1)) {
                    temp.append('/');
                    i++;
                    continue;
                }
            }
            if (current == '/') {
                pathBuilder.append(UrlEscapers.urlPathSegmentEscaper().escape(temp.toString()));
                pathBuilder.append('/');
                temp.setLength(0);
                continue;
            }
            temp.append(current);
        }
        if (temp.length() > 0) {
            pathBuilder.append(UrlEscapers.urlPathSegmentEscaper().escape(temp.toString()));
        }

        return pathBuilder;
    }

    private static boolean isSlashAtPos(@Nullable String in, int i) {
        in = StringUtils.defaultString(in);
        return in.length() > i && in.charAt(i) == '/';
    }

    /**
     * @param rawPath Use an empty string to remove the path.
     */
    public JURI setRawPath(@Nullable String rawPath) {
        startChange();

        this.rawPath = StringUtils.defaultString(rawPath);

        changed();
        return this;
    }

    @Nullable
    public String getRawPath() {
        if (rawPath == null) {
            rawPath = prototype.getRawPath();
        }
        return rawPath;
    }

    @Nullable
    public String getPath() {

        String raw = getRawPath();
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        return urlDecode(raw);
    }

    /**
     * @return true if a path is set: http://a.com/ : true, http://a.com : false.
     */
    public boolean isHavingPath() {
        String rawPath = StringUtils.defaultIfBlank(getRawPath(), "");
        return rawPath.length() > 0;
    }

    /**
     * @return true if a path is set and the path doesn't begin with a '/'.
     */
    public boolean isPathRelative() {
        String rawPath = StringUtils.defaultString(getRawPath());
        return rawPath.length() > 0 && !rawPath.startsWith("/");
    }

    /**
     * @return true if a path is set and the path begins with a '/'.
     */
    public boolean isPathAbsolute() {
        return isHavingPath() && !isPathRelative();
    }

    /**
     * Decodes the path segments on request.
     * @return a new list that the caller may manipulate. Empty string if no path or the root path is set. Returns
     * empty path segments (//as//df// has three empty
     * segments).
     */
    public List<String> getPathSegments() {
        ArrayList<String> result = getRawPathSegments();
        for (int i = 0; i < result.size(); i++) {
            String segment = result.get(i);
            result.set(i, urlDecode(segment));
        }
        return result;
    }

    /**
     * Doesn't decode the path segments.
     * @return a new list that the caller may manipulate. Empty string if no path or the root path is set. Returns
     * empty path segments (//as//df// has three empty
     * segments).
     */
    public ArrayList<String> getRawPathSegments() {
        return splitRawPath(StringUtils.defaultString(getRawPath()));
    }

    public static ArrayList<String> splitRawPath(String rawPath) {
        ArrayList<String> result = new ArrayList<>();
        if (rawPath.length() == 0) {
            return result;
        }
        boolean dropFirstSegment = false;
        boolean dropLastSegment = false;
        if (rawPath.startsWith("/")) {
            dropFirstSegment = true;
        }
        if (rawPath.endsWith("/")) {
            dropLastSegment = true;
        }

        boolean first = true;
        Iterator<String> splitIter = Splitter.on('/').split(rawPath).iterator();
        while (splitIter.hasNext()) {
            String current = splitIter.next();
            if (first) {
                first = false;
                if (dropFirstSegment) {
                    continue;
                }

            }

            if (!splitIter.hasNext() && dropLastSegment) {
                continue;
            }

            result.add(current);
        }
        return result;
    }

    public String getFragment() {
        if (fragment == null) {
            fragment = prototype.getFragment();
        }
        return fragment;
    }

    /**
     *
     * @param fragment null or "" clears the fragment part
     */
    public JURI setFragment(@Nullable String fragment) {
        startChange();

        this.fragment = StringUtils.defaultString(fragment);

        changed();
        return this;
    }

    public CharSequence buildQueryParametersString() {
        if (currentQueryParameters == null) {
            return prototype.getRawQuery();
        }

        return buildQueryParametersString(currentQueryParameters);
    }

    public static CharSequence buildQueryParametersString(Multimap<String, String> currentQueryParameters) {
        StringBuilder paramsString = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : currentQueryParameters.entries()) {
            if (!first) {
                paramsString.append('&');
            }
            String keyEnc = UrlEscapers.urlFormParameterEscaper().escape(entry.getKey());
            if (entry.getValue() != null) {
                String valueEnc = UrlEscapers.urlFormParameterEscaper().escape(entry.getValue());
                paramsString.append(keyEnc).append('=').append(valueEnc);
            } else {
                paramsString.append(keyEnc);
            }
            first = false;
        }

        return paramsString;
    }

    public Map<String, Collection<String>> getQueryParameters() {
        return getQueryParametersMultimap().asMap();
    }

    public String getQueryParameterFirstValue(String name) {
        Collection<String> vals = getQueryParametersMultimap().get(name);
        if (vals.isEmpty()) {
            return null;
        }
        return vals.iterator().next();
    }

    public boolean isHavingQueryParams() {
        if (currentQueryParameters != null && !currentQueryParameters.isEmpty()) {
            return true;
        }

        // to be sure must parse (for cases like ?& ):
        return !getQueryParametersMultimap().isEmpty();
    }

    protected Multimap<String, String> getQueryParametersMultimap() {
        if (currentQueryParameters != null) {
            return currentQueryParameters;
        }

        currentQueryParameters = parseQueryParameters(prototype);
        return currentQueryParameters;
    }

    public static Multimap<String, String> parseQueryParameters(URI uri) {
        return parseQueryParameters(StringUtils.defaultString(uri.getRawQuery()));
    }

    public static Multimap<String, String> parseQueryParameters(String rawQuery) {

        Multimap<String, String> result = createParamsMultimap();

        for (String singleParam : StringUtils.split(rawQuery, '&')) {
            if (StringUtils.isBlank(singleParam)) {
                continue;
            }
            String[] split = StringUtils.split(singleParam, '=');
            String key = urlDecode(split[0]);
            String value = "";
            if (split.length > 1) {
                value = urlDecode(split[1]);
            }
            result.put(key, value);
        }

        return result;
    }

    public JURI addQueryParameter(String name, String unencodedValue) {
        startChange();

        Multimap<String, String> params = getQueryParametersMultimap();
        params.put(name, unencodedValue);

        changed();
        return this;
    }

    public JURI addQueryParameters(String name, String... unencodedValues) {
        return addQueryParameters(name, Arrays.asList(unencodedValues));
    }

    public JURI addQueryParameters(String name, Collection<String> unencodedValues) {
        startChange();

        Multimap<String, String> params = getQueryParametersMultimap();
        params.putAll(name, unencodedValues);

        changed();
        return this;
    }

    public JURI removeQueryParameter(String name) {
        startChange();

        Multimap<String, String> params = getQueryParametersMultimap();
        params.removeAll(name);

        changed();
        return this;
    }

    public JURI replaceQueryParameter(String name, String unencodedValue) {
        startChange();

        Multimap<String, String> params = getQueryParametersMultimap();
        params.removeAll(name);
        params.put(name, unencodedValue);

        changed();
        return this;
    }

    public JURI replaceQueryParameters(String name, String... unencodedValues) {
        return replaceQueryParameters(name, Arrays.asList(unencodedValues));
    }

    public JURI replaceQueryParameters(String name, Collection<String> unencodedValues) {
        startChange();

        Multimap<String, String> params = getQueryParametersMultimap();
        params.removeAll(name);
        params.putAll(name, unencodedValues);

        changed();
        return this;
    }

    public JURI clearQueryParameters() {
        startChange();

        currentQueryParameters = createParamsMultimap();

        changed();
        return this;
    }

    protected static Multimap<String, String> createParamsMultimap() {
        return Multimaps.<String, String>newListMultimap(
                new LinkedHashMap<String, Collection<String>>(),
                new Supplier<ArrayList<String>>() {
                    @Override
                    public ArrayList<String> get() {
                        return new ArrayList<String>();
                    }
                });
    }

    public JURI addQueryParameters(Map<String, String> params) {
        startChange();

        Multimap<String, String> queryParameters = getQueryParametersMultimap();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            queryParameters.put(entry.getKey(), entry.getValue());
        }

        changed();
        return this;
    }

    public JURI addQueryParametersMulti(Map<String, Collection<String>> params) {
        startChange();

        Multimap<String, String> queryParameters = getQueryParametersMultimap();
        for (Map.Entry<String, Collection<String>> entry : params.entrySet()) {
            queryParameters.putAll(entry.getKey(), entry.getValue());
        }

        changed();
        return this;
    }

    public JURI replaceQueryParameters(Map<String, String> params) {
        startChange();

        Multimap<String, String> queryParameters = getQueryParametersMultimap();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            queryParameters.removeAll(entry.getKey());
            queryParameters.put(entry.getKey(), entry.getValue());
        }

        changed();
        return this;
    }

    public JURI replaceQueryParametersMulti(Map<String, Collection<String>> params) {
        startChange();

        Multimap<String, String> queryParameters = getQueryParametersMultimap();
        for (Map.Entry<String, Collection<String>> entry : params.entrySet()) {
            queryParameters.removeAll(entry.getKey());
            queryParameters.putAll(entry.getKey(), entry.getValue());
        }

        changed();
        return this;
    }

    /**
     * Navigate to a new URI.<br>
     * The navigate method tries to mimic browser behaviour:
     * 'What happens if you are on the current URI and click on the (relative or absolute) link.'<br>
     * The method changes the current JURI inplace, it does not create a new object.
     * If the path is altered, it is normalized.<br>
     * <br>
     * Examples:
     * <ul>
     *   <li>JURI.parse("http://example.com/a/b.html").navigate("http://www.google.com/search?q=2") =&gt; "http://www.google.com/search?q=2"</li>
     *   <li>JURI.parse("http://example.com/a/b.html").navigate("c.html")                           =&gt; "http://example.com/a/c.html"</li>
     *   <li>JURI.parse("http://example.com/a/b.html").navigate("c.html?z=1")                       =&gt; "http://example.com/a/c.html"</li>
     *   <li>JURI.parse("http://example.com/a/b.html").navigate("../c.html")                        =&gt; "http://example.com/c.html"</li>
     *   <li>JURI.parse("http://example.com/a/b.html").navigate("../../../../c.html")               =&gt; "http://example.com/c.html"</li>
     *   <li>JURI.parse("http://example.com/a/b.html").navigate("#anchor")                          =&gt; "http://example.com/a/b.html#anchor"</li>
     * </ul>
     *
     * @param path May be a relative path or a absolute URI to navigate to or an url fragment. 
     * @return this.
     */
    public JURI navigate(String path) {
        JURI newURI = JURI.parse(path);
        if (newURI.getHost() != null) {
            return this.resetBlank()
                    .parseNew(path);
        }

        String newPath = newURI.getPath();
        Map<String, Collection<String>> newQuery = newURI.getQueryParameters();
        String newFragment = newURI.getFragment();

        if (newURI.isHavingPath()) {
            // set new path (and maybe query and fragment):
            if (newURI.isPathRelative()) {
                // relative path:
                // calculate new normalized absolute path
                newPath = this.clone()
                        .addRawPath("../" + newURI.getPath())
                        .getCurrentUri()
                        .normalize()
                        .getPath();
                while (newPath.startsWith("/../")) {
                    newPath = newPath.substring(3);
                }
            }
            this.setPath(newPath)
                    .clearQueryParameters()
                    .addQueryParametersMulti(newQuery)
                    .setFragment(newFragment);
        } else if (newURI.isHavingQueryParams()) {
            // no path but query (and maybe fragment):
            // keep old path
            this.clearQueryParameters()
                    .addQueryParametersMulti(newQuery)
                    .setFragment(newFragment);
        } else {
            // only fragment:
            // keep old path and query
            this.setFragment(newFragment);
        }

        return this;
    }

    public boolean isNeedingCurrentUriConstruction() {
        return currentUri == null;
    }

    private void startChange() {
        this.changeUnderway = true;
        this.currentUri = null;
    }

    /**
     * Splitting into startChange and changed isn't strictly necessary - but it is when people debug the calling
     * methods.
     */
    private void changed() {
        this.changeUnderway = false;
        this.currentUri = null;
    }

    public static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Provides encoded URI - recreates the URI, should not be used if more changes will be applied to the wrapper.
     */
    @Override
    public String toString() {
        if (changeUnderway) {
            CharSequence detail;
            try {
                detail = buildNoSideEffects();
            } catch (URISyntaxException e) {
                detail = e.getMessage();
            }
            LOG.warn("Called toString while change is underway - this must only happen during debugging!");
            return detail.toString();
        }
        return getCurrentUri().toASCIIString();
    }

    /**
     * @throws IllegalStateException wrapping URISyntaxException if the internal state cannot result in a working URI.
     */
    @Override
    public JURI clone() throws IllegalStateException {
        JURI clone = null;
        try {
            clone = (JURI) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }

        if (clone.currentUri == null) {
            // goal here is to avoid having stateful objects in the clone and the original.
            try {
                clone.buildAndReset();
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }

        return clone;
    }
}
