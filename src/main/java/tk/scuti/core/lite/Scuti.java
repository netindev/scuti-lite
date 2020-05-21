package tk.scuti.core.lite;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/*
 * The MIT License
 *
 * Copyright 2019 netindev.
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
 */
public class Scuti {

   private JarOutputStream outputStream;

   private final File inputFile, outputFile;
   private final Map<String, ClassNode> classes;

   private static final double PACKAGE_VERSION = 0.03D;

   private static final Logger logger = LoggerFactory
         .getLogger(Scuti.class.getName());

   public Scuti(File inputFile, File outputFile) {
      this.inputFile = inputFile;
      this.outputFile = outputFile;
      this.classes = new HashMap<>();
   }

   public static void start(File inputFile, File outputFile) throws Throwable {
      new Scuti(inputFile, outputFile).run();
   }

   @Deprecated
   public static void main(String[] args) {

      System.out
            .println("Scuti-lite Java obfuscator written by netindev, version "
                  + PACKAGE_VERSION);
      if (args.length == 0) {
         logger.error(
               "Invalid arguments, please add to the arguments your input file and output file, e.g: \"java -jar scuti-lite.jar -in \"your input file.jar\" -out \"your output file.jar\".");
         return;
      }
      try {
         parseArgs(args);
      } catch (final Throwable e) {
         e.printStackTrace();
      }
   }

   @Deprecated
   private static void parseArgs(String[] args) {
      final Options options = new Options();
      options.addOption(
            Option.builder("in").hasArg().required().argName("jar").build());
      options.addOption(
            Option.builder("out").hasArg().required().argName("jar").build());
      try {
         final CommandLine parse = new DefaultParser().parse(options, args);
         final File inputFile = new File(parse.getOptionValue("in"));
         final File outputFile = new File(parse.getOptionValue("out"));
         if (!inputFile.exists() || !inputFile.canRead()) {
            logger.error("Input file can't be read or doesn't exists");
            return;
         }
         new Scuti(inputFile, outputFile).run();
      } catch (final Throwable e) {
         JOptionPane.showMessageDialog(null, "Something failed. \n" + e.getMessage());
         logger.error(e.getMessage());
         Runtime.getRuntime().exit(-1);
      }
   }

   private void run() throws Throwable {
      this.outputStream = new JarOutputStream(
            new FileOutputStream(this.outputFile));
      logger.info("Parsing input \"" + this.inputFile.getName() + "\"");
      this.parseInput();
      logger.info("Transforming classes");
      this.classes.values().forEach(classNode -> {
         // remove unnecessary insn
         this.removeNop(classNode);
         if (!Modifier.isInterface(classNode.access)) {
            this.transientAccess(classNode);
         }
         this.deprecatedAccess(classNode);
         // bad sources
         this.changeSource(classNode);
         // bad signatures
         this.changeSignature(classNode);
         // synthetic access (most decompilers doesn't show synthetic members)
         this.syntheticAccess(classNode);
         classNode.methods.forEach(methodNode -> {
            // bridge access (almost the same than synthetic)
            this.bridgeAccess(methodNode);
            // varargs access (crashes CFR when last parameter isn't array)
            this.varargsAccess(methodNode);
         });
      });
      logger.info("Dumping output \"" + this.outputFile.getName() + "\"");
      this.dumpClasses();

      JOptionPane.showMessageDialog(null, "Obfuscation finished.");
      logger.info("Obfuscation finished");
      Runtime.getRuntime().exit(0);
   }

   private void varargsAccess(MethodNode methodNode) {
      if ((methodNode.access & Opcodes.ACC_SYNTHETIC) == 0
            && (methodNode.access & Opcodes.ACC_BRIDGE) == 0) {
         methodNode.access |= Opcodes.ACC_VARARGS;
      }
   }

   private void bridgeAccess(MethodNode methodNode) {
      if (!methodNode.name.contains("<")
            && !Modifier.isAbstract(methodNode.access)) {
         methodNode.access |= Opcodes.ACC_BRIDGE;
      }
   }

   private void syntheticAccess(ClassNode classNode) {
      classNode.access |= Opcodes.ACC_SYNTHETIC;
      classNode.fields
            .forEach(fieldNode -> fieldNode.access |= Opcodes.ACC_SYNTHETIC);
      classNode.methods
            .forEach(methodNode -> methodNode.access |= Opcodes.ACC_SYNTHETIC);
   }

   private void changeSource(ClassNode classNode) {
      classNode.sourceFile = this.getMassiveString();
      classNode.sourceDebug = this.getMassiveString();
   }

   private void changeSignature(ClassNode classNode) {
      classNode.signature = this.getMassiveString();
      classNode.fields.forEach(
            fieldNode -> fieldNode.signature = this.getMassiveString());
      classNode.methods.forEach(
            methodNode -> methodNode.signature = this.getMassiveString());
   }

   private void deprecatedAccess(ClassNode classNode) {
      classNode.access |= Opcodes.ACC_DEPRECATED;
      classNode.methods
            .forEach(methodNode -> methodNode.access |= Opcodes.ACC_DEPRECATED);
      classNode.fields
            .forEach(fieldNode -> fieldNode.access |= Opcodes.ACC_DEPRECATED);
   }

   private void transientAccess(ClassNode classNode) {
      classNode.fields
            .forEach(fieldNode -> fieldNode.access |= Opcodes.ACC_TRANSIENT);
   }
   
   private void removeNop(ClassNode classNode) {
      classNode.methods.parallelStream().forEach(
            methodNode -> Arrays.stream(methodNode.instructions.toArray())
                  .filter(insnNode -> insnNode.getOpcode() == Opcodes.NOP)
                  .forEach(insnNode -> {
                     methodNode.instructions.remove(insnNode);
                  }));
   }

   private void parseInput() throws IOException {
      final JarFile jarFile = new JarFile(this.inputFile);
      jarFile.stream().forEach(entry -> {
         try {
            if (entry.getName().endsWith(".class")) {
               final ClassReader classReader = new ClassReader(
                     jarFile.getInputStream(entry));
               final ClassNode classNode = new ClassNode();
               classReader.accept(classNode, ClassReader.SKIP_DEBUG);
               this.classes.put(classNode.name, classNode);
            } else if (!entry.isDirectory()) {
               this.outputStream.putNextEntry(new ZipEntry(entry.getName()));
               this.outputStream
                     .write(this.toByteArray(jarFile.getInputStream(entry)));
               this.outputStream.closeEntry();
            }
         } catch (final Exception e) {
            e.printStackTrace();
         }
      });
      jarFile.close();
   }

   private void dumpClasses() throws IOException {
      this.classes.values().forEach(classNode -> {
         ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
         try {
            classNode.accept(classWriter);
            final JarEntry jarEntry = new JarEntry(
                  classNode.name.concat(".class"));
            this.outputStream.putNextEntry(jarEntry);
            this.outputStream.write(classWriter.toByteArray());
         } catch (final Exception e) {
            logger.error("Error while writing " + classNode.name, e);
         }
      });
      this.outputStream.close();
   }

   private String getMassiveString() {
      final StringBuilder builder = new StringBuilder();
      for (int i = 0; i < Short.MAX_VALUE; i++) {
         builder.append(" ");
      }
      return builder.toString();
   }

   private byte[] toByteArray(InputStream inputStream) throws IOException {
      final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      final byte[] buffer = new byte[0xFFFF];
      int length;
      while ((length = inputStream.read(buffer)) != -1) {
         outputStream.write(buffer, 0, length);
      }
      outputStream.flush();
      return outputStream.toByteArray();
   }

}
