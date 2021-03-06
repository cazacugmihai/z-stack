/**
 * Copyright 2013, Landz and its contributors. All rights reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package bugs.openjdk.hotspot.nullmethodopt;

import java.util.function.BooleanSupplier;

/**
 * Created by jin on 12/26/13.
 */
public class Runner0 {

  public static void main(String[] args) throws Exception {
    new Runner().invoke();
  }

  //XXX: comment out the following line to see the magic
//  public static void asdsadsa(BooleanSupplier a){}

}
