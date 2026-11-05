// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/helpers/ConfigHelper.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.helpers;

import com.mineagent.sbe.altoclef.Debug;
import com.mineagent.sbe.altoclef.util.serialization.IFailableConfigFile;
import com.mineagent.sbe.altoclef.util.serialization.IListConfigFile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConfigHelper {
   private static final String ALTO_FOLDER = "me/sailex/altoclef";
   private static final HashMap<String, Runnable> loadedConfigs = new HashMap<>();

   private static File getConfigFile(String path) {
      // SMH: under the game's config directory (it was a folder "me/sailex/altoclef" wherever the server was started).
      return com.mineagent.sbe.common.ServerHooks.configDir().resolve("mineagent").resolve("altoclef").resolve(path).toFile();
   }

   public static void reloadAllConfigs() {
      for (Runnable config : loadedConfigs.values()) {
         config.run();
      }
   }

   private static <T> T getConfig(String path, Supplier<T> getDefault, Class<T> classToLoad) {
      // SMH: its settings' defaults; no file (the mod sets what it needs in code).
      return getDefault.get();
   }

   public static <T> void loadConfig(String path, Supplier<T> getDefault, Class<T> classToLoad, Consumer<T> onReload) {
      T config = getConfig(path, getDefault, classToLoad);
      loadedConfigs.put(path, () -> onReload.accept(config));
      onReload.accept(config);
   }

   public static <T> void saveConfig(String path, T config) {
      // SMH: nothing written (see getConfig).
   }

   private static void createParentDirectories(File file) {
      try {
         Path parentPath = file.getParentFile().toPath();
         Files.createDirectories(parentPath);
      } catch (IOException var2) {
         System.err.println("Failed to create parent directories: " + var2.getMessage());
      }
   }

   private static void enablePrettyPrinting(Object mapper) {
      // SMH: not used (see getConfig).
   }

   private static <T> void writeConfigToFile(Object objectMapper, File configFile, T configData) throws IOException {
      // SMH: not used (see getConfig).
   }

   private static void handleIOException(IOException exception) {
      String errorMessage = "An IOException occurred: " + exception.getMessage();
      System.err.println(errorMessage);
   }

   private static <T extends IListConfigFile> T getListConfig(String path, Supplier<T> getDefault) {
      IListConfigFile iListConfigFile = getDefault.get();
      iListConfigFile.onLoadStart();
      File configFile = getConfigFile(path);
      if (!configFile.exists()) {
         return (T)iListConfigFile;
      } else {
         try {
            FileInputStream fis = new FileInputStream(configFile);

            try {
               Scanner scanner = new Scanner(fis);

               try {
                  while (scanner.hasNextLine()) {
                     String line = trimComment(scanner.nextLine()).trim();
                     if (!line.isEmpty()) {
                        iListConfigFile.addLine(line);
                     }
                  }

                  scanner.close();
               } catch (Throwable var10) {
                  try {
                     scanner.close();
                  } catch (Throwable var9) {
                     var10.addSuppressed(var9);
                  }

                  throw var10;
               }

               fis.close();
               return (T)iListConfigFile;
            } catch (Throwable var11) {
               try {
                  fis.close();
               } catch (Throwable var8) {
                  var11.addSuppressed(var8);
               }

               throw var11;
            }
         } catch (IOException var12) {
            var12.printStackTrace();
            return null;
         }
      }
   }

   public static <T extends IListConfigFile> void loadListConfig(String path, Supplier<T> getDefault, Consumer<T> onReload) {
      T result = getListConfig(path, getDefault);
      loadedConfigs.put(path, () -> onReload.accept(result));
      onReload.accept(result);
   }

   private static String trimComment(String line) {
      int poundIndex = line.indexOf(35);
      return poundIndex == -1 ? line : line.substring(0, poundIndex);
   }

   public static void ensureCommentedListFileExists(String path, String startingComment) {
      File configFile = getConfigFile(path);
      if (!configFile.exists()) {
         StringBuilder commentBuilder = new StringBuilder();

         for (String line : startingComment.split("\\r?\\n")) {
            if (!line.isEmpty()) {
               commentBuilder.append("# ").append(line).append("\n");
            }
         }

         try {
            Files.write(configFile.toPath(), commentBuilder.toString().getBytes());
         } catch (IOException var8) {
            handleException(var8);
         }
      }
   }

   private static void handleException(IOException exception) {
      System.err.println("An error occurred: " + exception.getMessage());
   }
}
