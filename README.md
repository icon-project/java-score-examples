# Java SCORE Examples

This repository contains SCORE (Smart Contract for ICON) examples written in Java.

## Requirements

You need to install JDK 11 or later version. Visit [OpenJDK.net](http://openjdk.java.net/) for prebuilt binaries.
Or you can install a proper OpenJDK package from your OS vendors.

In macOS:
```
$ brew tap AdoptOpenJDK/openjdk
$ brew cask install adoptopenjdk11
```

In Linux (Ubuntu 18.04):
```
$ sudo apt install openjdk-11-jdk
```

## How to Run

### 1. Build the project

```
$ ./gradlew build
```
The compiled jar bundle will be generated at `./hello-world/build/libs/hello-world.jar`.

### 2. Optimize the jar

You need to optimize your jar bundle before you deploy it to local or ICON networks.
This involves some pre-processing to ensure the actual deployment successful.

`gradle-javaee-plugin` is a Gradle plugin to automate the process of generating the optimized jar bundle.
Run the `optimizedJar` task to generate the optimized jar bundle.

```
$ ./gradlew optimizedJar
```
The output jar will be located at `./hello-world/build/libs/hello-world-optimized.jar`.

### 3. Deploy the optimized jar

Now you can deploy the optimized jar to ICON networks that support the Java SCORE execution environment.
Assuming you are running a local network that is listening on port 9082 for incoming requests,
you can create a deploy transaction with the optimized jar and deploy it to the local network as follows.

```
$ goloop rpc sendtx deploy ./hello-world/build/libs/hello-world-optimized.jar \
    --uri http://localhost:9082/api/v3 \
    --key_store <your_wallet_json> --key_password <password> \
    --nid 3 --step_limit=1000000 \
    --content_type application/java \
    --param name=Alice
```

**[Note]** The content type should be `application/java` instead of `application/zip` to differentiate it with the Python SCORE deployment.

### 4. Verify the execution

Check the deployed SCORE address first using the `txresult` command.
```
$ goloop rpc txresult <tx_hash> --uri http://localhost:9082/api/v3
{
  ...
  "scoreAddress": "cxaa736426a9caed44c59520e94da2d64888d9241b",
  ...
}
```

Then you can invoke `getGreeting` method via the following `call` command.
```
$ goloop rpc call --to <score_address> --method getGreeting
"Hello Alice!"
```

## Java SCORE Structure

### Comparison to Python SCORE

| Name               | Python SCORE                 | Java SCORE                  |
|--------------------|------------------------------|-----------------------------|
| External decorator | `@external`                  | `@External`                 |
| - (readonly)       | `@external(readonly=True)`   | `@External(readonly=true)`  |
| Payable decorator  | `@payable`                   | `@Payable`                  |
| Eventlog decorator | `@eventlog`                  | `@EventLog`                 |
| - (indexed)        | `@eventlog(indexed=1)`       | `@EventLog(indexed=1)`      |
| fallback signature | `def fallback`               | `void fallback()`           |
| SCORE initialize   | override `on_install` method | define a public constructor |
| Default parameters | native language support      | `@Optional`                 |

**[NOTE]** All external Java methods must have a `public` modifier, and should be instance methods.

### How to invoke a external method of another SCORE

One SCORE can invoke an external method of another SCORE using the following APIs.

```java
// [package score.Context]
public static Object call(Address targetAddress, String method, Object... params);

public static Object call(BigInteger value,
                          Address targetAddress, String method, Object... params);

public static Object call(BigInteger value, BigInteger stepLimit,
                          Address targetAddress, String method, Object... params);
```

The following example is for calling `tokenFallback`.
```java
if (_to.isContract()) {
    Context.call(_to, "tokenFallback", _from, _value, dataBytes);
}
```

## References

* [SCORE API document](http://ci.arch.iconloop.com/pages/arch/goloop/master/javadoc/)
* [Goloop document](http://ci.arch.iconloop.com/pages/arch/goloop/master/doc/)
* [Goloop CLI command reference](http://ci.arch.iconloop.com/pages/arch/goloop/master/doc/goloop_cli.html)

## Licenses

This project follows the Apache 2.0 License. Please refer to [LICENSE](https://www.apache.org/licenses/LICENSE-2.0) for details.
