Build a modified jdk. The modifications are based on jdk-11+28, so you will need to use
git to checkout this tag.

Create a JinixOS directory

From the JinixOS directory, checkout the Jinix repo. 

Apply patch.txt with --ignore-space-change

bash configure --disable-warnings-as-errors

make product-images

Set JAVA_HOME to reference the newly created jdk in the jdk build directory. Maven
will use JAVA_HOME to locate the jdk used to compile during the build process. Jinix
code requires this modified jdk, and will not compile against a stock jdk.

Export JINIX_JAVA_HOME to reference the newly created jdk in the jdk build directory.
Jinix shell scripts will use this varialbe to locate the jdk.

From the JinixOS directory, checkout the following Jinix repos from Github
CoreTranslators
CoreUtilities
KernelLogging
sshd
Translators

Create the following directories
  lib
  config

Copy the following directories from JinixOS/Jinix into the JinixOS directory:
  scripts
  config

Using the java jar utility, unjar JinixOS/Jinix/root.jar into the JinixOS directory
  jar -xf Jinix/root.jar

Build each of the maven projects by entering the project directory and executing 'mvn install'. Build
the projects in the following order:

Jinix
KernelLogging
CoreTranslators
CoreUtilities
Translators
sshd

Copy the NativeFileSystem translator from JinixOS/root/bin to JinixOS/lib

Start the Jinix kernel by running script/jinix from the JinixOS directory.



