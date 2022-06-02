package io.github.gaming32.javayield;

import io.github.gaming32.javayield.impl.SupplierIterator;
import java.util.Iterator;

public class OtherTest {
   public static Iterable<Integer> generatorTest(int param) {
      return SupplierIterator.$createIterableGenerator(() -> {
         label24: {
            Object var10000;
            switch(var1[0]) {
               case 0:
               case 1:
                  if (paramx[0] > 0) {
                     Integer var4 = paramx[0] *= 3;
                     var1[0] = 1;
                     return var4;
                  }

                  var10000 = "hello".chars().iterator();
                  break;
               case 2:
                  var10000 = $state[0];
                  break;
               case 3:
               default:
                  break label24;
            }

            if (((Iterator)var10000).hasNext()) {
               $state[0] = (int)var10000;
               var10000 = ((Iterator)var10000).next();
               var1[0] = 2;
               return var10000;
            }
         }

         var1[0] = 3;
         return SupplierIterator.$COMPLETE;
      });
   }
}
