**Building the Jinix JDK**

Build a modified jdk. The modifications are based on jdk-11+28, so you will need to use
git to checkout this tag.

git clone https://github.com/openjdk/jdk11u.git
git checkout jdk-11+28

Create a JinixOS directory

From the JinixOS directory, checkout the Jinix repo. https://github.com/rowland66/Jinix.git

Patch the jdk by appling the patch.txt files in Jinix/jdk and Jinix/jinix-spi. Use the
--ignore-space-change option.

Configure the jdk build by running 

'bash configure --disable-warnings-as-errors --with-version-pre=jinix'

See build instructions available at doc/building.md. You will need a JDK installed and you
can specify the jdk location with --with-boot-jdk

Build the jdk by running 'make product-images'

**Setting up the Environment**

Export JAVA_HOME to reference the newly created jdk in the jdk build directory. Maven
will use JAVA_HOME to locate the jdk used to compile during the build process. Jinix
code requires this modified jdk, and will not compile against a stock jdk. This setting
is only required during the Jinux build, and is not required at runtime.

Export JINIX_JAVA_HOME to reference the newly created jdk in the jdk build directory.
Same as JAVA_HOME above. Jinix shell scripts and the Jinix Kernel will use this varialbe 
to locate the jdk to use to run Jinix programs. This setting is required at runtime when
starting the Jinix Kernel.

Optionally export JINIX_HOME to reference the JinixOS directory. Setting JINIX_HOME is not
required, but setting it will allow you to start Jinix from any directory. If this variable
is not set, Jinix must be started from the JinixOS directory.

**Installing and Building Jinix**

From the JinixOS directory, checkout the following Jinix repos from Github
CoreTranslators (https://github.com/rowland66/CoreTranslators.git)
CoreUtilities (https://github.com/rowland66/CoreUtilities.git)
KernelLogging (https://github.com/rowland66/KernelLogging.git)
sshd (https://github.com/rowland66/sshd.git)
Translators (https://github.com/rowland66/Translators.git)

Create the following directories
  lib

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

Copy the NativeFileSystem.jar file from JinixOS/root/bin to JinixOS/lib.

**Running Jinix**

Start the Jinix kernel by running './scripts/jinix' from the JinixOS directory.

Start an ssh client and connect to the Jinix ssh daemon on port 8000 using the command 'ssh -p 8000 localhost'

Type <Enter> for password as no password is required. You should see a Jinix shell prompt '>'. You can begin
exploring Jinix.