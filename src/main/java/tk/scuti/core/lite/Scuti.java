package tk.scuti.core.lite;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

/*
 * The MIT License
 *
 * Copyright 2018 netindev.
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
   private final Map<String, ClassNode> classes, libraries;

   private static final double PACKAGE_VERSION = 0.01D;

   public Scuti(File inputFile, File outputFile, List<File> libraries) {
      this.inputFile = inputFile;
      this.outputFile = outputFile;
      this.classes = new HashMap<>();
      this.libraries = new HashMap<>();
      if (libraries != null) {
         try {
            System.out.println("Loading libraries");
            this.parseLibraries(libraries);
         } catch (final IOException e) {
            e.printStackTrace();
         }
      }
   }

   public static void main(String[] args) {
      if (args.length == 0) {
         System.err.println(
               "Invalid arguments, please add to the arguments your input file.");
         return;
      }
      try {
         System.out.println(
               "Scuti-lite Java obfuscator written by netindev, version "
                     + PACKAGE_VERSION);
         parseArgs(args);
      } catch (final Throwable e) {
         e.printStackTrace();
      }
   }

   private static void parseArgs(String[] args) {
      final Options options = new Options();
      options.addOption(
            Option.builder("in").hasArg().required().argName("jar").build());
      options.addOption(
            Option.builder("out").hasArg().required().argName("jar").build());
      options.addOption(Option.builder("lib").hasArg().argName("jar").build());
      try {
         final CommandLine parse = new DefaultParser().parse(options, args);
         final File inputFile = new File(parse.getOptionValue("in"));
         final File outputFile = new File(parse.getOptionValue("out"));
         if (!inputFile.exists() || !inputFile.canRead()) {
            System.err.println("Input file can't be read or doesn't exists");
            return;
         }
         final List<File> libraries = new ArrayList<>();
         if (parse.getOptionValues("lib") != null) {
            final String[] parseLibraries = parse.getOptionValues("lib");
            Arrays.asList(parseLibraries).forEach(library -> {
               final File file = new File(library);
               if (file.canRead() && file.exists()) {
                  libraries.add(file);
               } else {
                  System.err.println("One of the library list can't be read");
                  return;
               }
            });
         }
         new Scuti(inputFile, outputFile, libraries).run();
      } catch (final ParseException e) {
         System.err.println(e.getMessage());
         System.err.println(
               "Correct use: java -jar scuti-lite.jar -in \"x.jar\" -out \"y.jar\" -lib* \"rt.jar\" -lib \"bar.jar\"");
      } catch (final Throwable e) {
         e.printStackTrace();
      }
   }

   private void run() throws Throwable {
      this.outputStream = new JarOutputStream(
            new FileOutputStream(this.outputFile));
      System.out.println("Parsing input");
      this.parseInput();
      System.out.println("Transforming classes");
      this.classes.values().forEach(classNode -> {
         // bad signatures
         this.changeSignature(classNode);
         // synthetic access (most decompilers doesn't show synthetic members)
         this.syntheticAccess(classNode);
         // bridge access (almost the same than synthetic)
         this.bridgeAccess(classNode);
         // clean members
         this.cleanMembers(classNode);
      });
      System.out.println("Dumping output");
      this.dumpClasses();
      System.out.println("Obfuscation finished");
   }

   private void cleanMembers(ClassNode classNode) {
      classNode.methods.forEach(methodNode -> {
         this.clean(methodNode.attrs);
         this.clean(methodNode.parameters);
         this.clean(methodNode.localVariables);
      });
      classNode.fields.forEach(fieldNode -> {
         this.clean(fieldNode.attrs);
      });
      this.clean(classNode.innerClasses);
      this.clean(classNode.attrs);
   }

   private void clean(List<?> list) {
      if (list != null) {
         list.clear();
      }
   }

   private void bridgeAccess(ClassNode classNode) {
      classNode.methods.stream()
            .filter(methodNode -> !methodNode.name.contains("<")
                  && !Modifier.isAbstract(methodNode.access))
            .forEach(methodNode -> methodNode.access |= Opcodes.ACC_BRIDGE);
   }

   private void syntheticAccess(ClassNode classNode) {
      classNode.access |= Opcodes.ACC_SYNTHETIC;
      classNode.fields
            .forEach(fieldNode -> fieldNode.access |= Opcodes.ACC_SYNTHETIC);
      classNode.methods
            .forEach(methodNode -> methodNode.access |= Opcodes.ACC_SYNTHETIC);
   }

   private void changeSignature(ClassNode classNode) {
      classNode.sourceFile = this.getMassiveString();
      classNode.sourceDebug = this.getMassiveString();
      classNode.signature = this.getMassiveString();
      classNode.fields.forEach(
            fieldNode -> fieldNode.signature = this.getMassiveString());
      classNode.methods.forEach(
            methodNode -> methodNode.signature = this.getMassiveString());
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

   private void parseLibraries(List<File> list) throws IOException {
      for (final File file : list) {
         final JarFile jarFile = new JarFile(file);
         jarFile.stream().forEach(entry -> {
            try {
               if (entry.getName().endsWith(".class")) {
                  final ClassReader classReader = new ClassReader(
                        jarFile.getInputStream(entry));
                  final ClassNode classNode = new ClassNode();
                  classReader.accept(classNode, ClassReader.SKIP_FRAMES
                        | ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE);
                  this.libraries.put(classNode.name, classNode);
               }
            } catch (final Exception e) {
               e.printStackTrace();
            }
         });
         jarFile.close();
      }
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
            System.err.println("Error while writing " + classNode.name);
            e.printStackTrace();
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
