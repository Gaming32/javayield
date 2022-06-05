# Javayield

Javayield is a library that adds generator functions to Java.

## Setup

### Maven

Simply add this to the dependencies section of your `pom.xml`:

```xml
<dependency>
  <groupId>io.github.gaming32.javayield</groupId>
  <artifactId>javayield-javac</artifactId>
  <version>1.0-SNAPSHOT</version>
  <scope>provided</scope>
</dependency>
<dependency>
  <groupId>io.github.gaming32.javayield</groupId>
  <artifactId>javayield-runtime</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

And this to the plugins section of your `pom.xml`:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <version>3.8.1</version>
  <configuration>
    <compilerArgs>
      <compilerArg>-Xplugin:javayield</compilerArg>
    </compilerArgs>
  </configuration>
</plugin>
```

### IntelliJ

#### With Javac compiler (the default)

You're all set up! IntelliJ reads the `compilerArgs` from your `pom.xml` and configures itself automatically.

#### With ECJ compiler

Setting up with ECJ in IntelliJ is similar to setting up with Visual Studio Code, as Visual Studio Code uses ECJ.

  1. Open the IntelliJ settings
     ![Open IntelliJ settings](images/intellij-ecj/Open%20IntelliJ%20settings.png)
  2. Go to Java Compiler settings
     ![Go to Java Compiler settings](images/intellij-ecj/Go%20to%20Java%20Compiler%20settings.png)
  3. Add `-javaagent:"/path/to/javayield-ecj.jar"` to the end of "Additional command-line parameters"
     ![Add the arg](images/intellij-ecj/Add%20the%20arg.png)

### Visual Studio Code

Setting up with Visual Studio Code is similar to setting up with ECJ in IntelliJ, as Visual Studio Code uses ECJ.

  1. Open the Visual Studio Code settings
     ![Settings gear](images/vscode/Settings%20gear.png)
     ![Open settings](images/vscode/Open%20settings.png)
  2. Search for "vmargs"
     ![Search for vmargs](images/vscode/Search%20for%20vmargs.png)
  3. Edit the vmargs in settings.json
     ![Edit the vmargs](images/vscode/Edit%20the%20vmargs.png)
  4. Add `-javaagent:\"/path/to/javayield-ecj.jar\"` to the end of the vmargs
     ![Add the vmarg](images/vscode/Add%20the%20vmarg.png)
