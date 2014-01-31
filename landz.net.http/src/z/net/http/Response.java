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

package z.net.http;

import z.offheap.buffer.ByteBuffer;

/**
 * Created by jin on 1/24/14.
 */
public class Response {

//  public int statusCode;
  //FIXME: now only works for static buffer
  //FIXME:    (a static content cached in buffer without )
  ByteBuffer buffer;

}
