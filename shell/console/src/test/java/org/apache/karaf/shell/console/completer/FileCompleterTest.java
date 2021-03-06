/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.shell.console.completer;

import java.io.File;
import java.util.Arrays;

import org.apache.felix.gogo.commands.Action;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.basic.SimpleCommand;
import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.console.Completer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FileCompleterTest extends CompleterTestSupport {

    @Test
    public void testCompleteArgumnets() throws Exception {
        CommandSession session = new DummyCommandSession();
        Completer comp = new ArgumentCompleter(session, new SimpleCommand(MyAction.class), "my:action");

        // arg 0
        assertEquals(Arrays.asList("src/"), complete(comp, "action s"));
        assertEquals(Arrays.asList("main/"), complete(comp, "action src/m"));
        assertEquals(Arrays.asList("java/"), complete(comp, "action src/main/j"));
    }

    public static class MyAction implements Action {
        @Argument(index = 0)
        File foo;
        @Argument(index = 1)
        File bar;

        public Object execute(CommandSession session) throws Exception {
            return null;
        }
    }

}
