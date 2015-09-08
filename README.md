JURI
====


URIs like they should be
------------------------

URI parsing and manipulation for Java.


### Parsing and reading

Pass any URL into the constructor (similiar to java.net.URI but without the bugs in parsing parameters, user info, etc.):

```java
JURI uri = JURI.parse("http://user:pass@www.test.com:81/path/index.html?q=books#fragment");
JURI uri = JURI.create(new URI("http://user:pass@www.test.com:81/path/index.html?q=books#fragment"));
```

Use property methods to get at the various parts (like java.net.URI but more convenient):

```java
uri.getScheme()          // http
uri.getUser()            // user
uri.getPassword()        // pass
uri.getHost()            // www.test.com
uri.getPort()            // 81
uri.getPath()            // /path/index.html
uri.getPathSegments()    // ["path","index.html"]
uri.getQueryParameters() // {"q": "books"} as Map<String, Collecion<String>>
uri.getFragment()        // fragment
```

### Modifying and writing

JURI can modify and extend your URIs. Setters and other modifying methods are chainable:

```java
JURI.parse(uri)
    .setScheme(scheme)
    .setUserInfo(user, password)
    .setHost(host)
    .setPort(port)
    .addPathSegment(directory)
    .addQueryParameter(key, value)
    .setFragment()
    .toString();
```

Do magic with query parameters:

```java
uri.addQueryParameter("q", "value");
uri.replaceQueryParameter("q", "value");
uri.removeQueryParameter("q");
uri.clearQueryParameters();
JURI.parse("...?q=X&q=Y").replaceQueryParameter("q", "value").toString() // -> "...?q=value"
```

Read, set and alter paths:
```java
JURI uri = JURI.parse("blah/blub")
uri.addPathSegment("sub%20dir")
uri.getRawPath() // -> "blah/blub/sub%20dir"
uri.getPath() // -> "blah/blub/sub dir"
```

Dependencies
------------

 + Guava for escaping.
 + A lib for Nullable etc.
 + StringUtils from Apache lang-commons.
 + slf4j-api for logging.


Related work
------------

Why should I use JURI instead of java.net.URI?
URI helps when parsing URIs. Not when building or mutating them.

Why should I use JURI instead of java.net.URL?
URL does many things. And many unexpected things. An example:
What do you expect this line to do:
```java
url1.equals(url2);
```
Do you expect it to do blocking DNS requests and compare IP adresses? If using java.net.URL you better should...

Slightly inspired by [jsuri](https://github.com/derek-watson/jsUri).
