// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/helpers/StlHelper.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.helpers;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;

public interface StlHelper {
   static <T> Comparator<T> compareValues(Function<T, Double> getValue) {
      return (left, right) -> (int)Math.signum(getValue.apply(left) - getValue.apply(right));
   }

   static <T> String toString(Collection<T> thing, Function<T, String> toStringFunc) {
      StringBuilder result = new StringBuilder();
      result.append("[");
      int i = 0;

      for (T item : thing) {
         result.append(toStringFunc.apply(item));
         if (i != thing.size() - 1) {
            result.append(",");
         }

         i++;
      }

      result.append("]");
      return result.toString();
   }

   static <T> String toString(T[] thing, Function<T, String> toStringFunc) {
      try {
         return toString(Arrays.asList(thing), toStringFunc);
      } catch (NullPointerException var3) {
         return "<null>";
      }
   }
}
