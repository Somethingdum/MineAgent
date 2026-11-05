package me.sailex.altoclef.util.serialization;

public interface IFailableConfigFile {
   void onFailLoad();

   boolean failedToLoad();
}
