#!/usr/bin/env bash
set -e
rm -fr bin
path_dir=${PWD##*/}
rm -f "${path_dir}.jar"
mkdir bin
cd bin
find ../lib -type f -name "*.jar" | while read f
do
  echo "Compiling $f"
  jar xf "$f"
done
rm -fr META-INF/*.SF
rm -fr META-INF/*.RSA
rm -fr META-INF/*.DSA
rm -fr META-INF/MANIFEST.MF
rm -f module-info.class
echo "Manifest-Version: 1.0" >> META-INF/MANIFEST.MF
echo "Main-Class: App" >> META-INF/MANIFEST.MF
echo "" >> META-INF/MANIFEST.MF
echo "" >> META-INF/MANIFEST.MF
cd ../
javac -cp "src/:lib/*:lib/lib/*" src/App.java -d bin
jar cfM "${path_dir}.jar" -C bin/ .
rm -fr bin
