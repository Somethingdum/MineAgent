package me.sailex.altoclef;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Debug {

   private static final Logger logger = LogManager.getLogger();

   public static void logInternal(String message) {
       logger.info("{}{}", getLogPrefix(), message);
   }

   public static void logInternal(String format, Object... args) {
      logInternal(String.format(format, args));
   }

   private static String getLogPrefix() {
      return "[Alto Clef] ";
   }

   public static void logMessage(String message, boolean prefix) {
      logInternal(message);
   }

   public static void logUserMessage(String message) {
      logInternal(message);
   }

   public static void logMessage(String message) {
      logMessage(message, true);
   }

   public static void logMessage(String format, Object... args) {
      logMessage(String.format(format, args));
   }

   public static void logWarning(String message) {
      logger.warn("{}{}", getLogPrefix(), message);
   }

   public static void logWarning(String format, Object... args) {
      logWarning(String.format(format, args));
   }

   public static void logError(String message) {
      String stacktrace = getStack(2);
      logger.error("{}{} at: {}", getLogPrefix(), message, stacktrace);
   }

   public static void logError(String format, Object... args) {
      logError(String.format(format, args));
   }

   public static void logStack() {
      logInternal("STACKTRACE: \n" + getStack(2));
   }

   private static String getStack(int toSkip) {
      StringBuilder stacktrace = new StringBuilder();

      for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
         if (toSkip-- <= 0) {
            stacktrace.append(ste.toString()).append("\n");
         }
      }

      return stacktrace.toString();
   }
}
