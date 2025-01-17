/*
 * Copyright 2019-2022 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.cloudnetservice.cloudnet.driver.network.netty;

import eu.cloudnetservice.cloudnet.driver.DriverEnvironment;
import eu.cloudnetservice.cloudnet.driver.DriverTestUtil;
import io.netty.buffer.Unpooled;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class NettyUtilTest {

  @Test
  void testWrapperThreadAmount() {
    Mockito
      .when(DriverTestUtil.mockAndSetDriverInstance().environment())
      .thenReturn(DriverEnvironment.WRAPPER);

    Assertions.assertEquals(4, NettyUtil.threadAmount());
  }

  @Test
  void testNodeThreadAmount() {
    Mockito
      .when(DriverTestUtil.mockAndSetDriverInstance().environment())
      .thenReturn(DriverEnvironment.NODE);

    Assertions.assertTrue(NettyUtil.threadAmount() >= 8);
  }

  @RepeatedTest(30)
  public void testVarIntCoding() {
    var byteBuf = Unpooled.buffer();
    var i = ThreadLocalRandom.current().nextInt();

    try {
      Assertions.assertNotNull(NettyUtil.writeVarInt(byteBuf, i));
      Assertions.assertEquals(i, NettyUtil.readVarInt(byteBuf));
    } finally {
      byteBuf.release();
    }
  }
}
