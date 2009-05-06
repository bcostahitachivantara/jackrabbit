/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.test.api.observation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.EventJournal;

/**
 * <code>EventJournalTest</code> performs EventJournal tests.
 */
public class EventJournalTest extends AbstractObservationTest {

    private EventJournal journal;

    protected void setUp() throws Exception {
        super.setUp();
        journal = obsMgr.getEventJournal();
    }

    public void testSkipToNow() throws RepositoryException {
        // skip everything
        journal.skipTo(System.currentTimeMillis());
        assertFalse(journal.hasNext());
    }

    public void testSkipTo() throws Exception {
        long time = System.currentTimeMillis();

        // add some nodes
        Node n1 = testRootNode.addNode(nodeName1);
        Node n2 = testRootNode.addNode(nodeName2);

        // make sure some time passed otherwise we might
        // skip this change as well.
        while (time == System.currentTimeMillis()) {
            Thread.sleep(1);
        }

        // now save
        superuser.save();

        journal.skipTo(time);
        // at least the two added nodes must be returned by the journal
        checkJournal(new String[]{n1.getPath(), n2.getPath()}, new String[0]);
    }

    public void testLiveJournal() throws RepositoryException {
        journal.skipTo(System.currentTimeMillis());
        assertFalse(journal.hasNext());

        testRootNode.addNode(nodeName1);
        superuser.save();

        assertTrue(journal.hasNext());
    }

    public void testWorkspaceSeparation() throws RepositoryException {
        journal.skipTo(System.currentTimeMillis());
        assertFalse(journal.hasNext());

        Session session = helper.getSuperuserSession(workspaceName);
        try {
            Node rootNode = session.getRootNode();
            if (rootNode.hasNode(nodeName1)) {
                rootNode.getNode(nodeName1).remove();
            } else {
                rootNode.addNode(nodeName1);
            }
            session.save();
        } finally {
            session.logout();
        }

        assertFalse(journal.hasNext());
    }

    public void testIsDeepTrue() throws RepositoryException {
        Node n1 = testRootNode.addNode(nodeName1);
        Node n2 = n1.addNode(nodeName2);

        journal = obsMgr.getEventJournal();
        journal.skipTo(System.currentTimeMillis());

        superuser.save();

        checkJournal(new String[]{n1.getPath(), n2.getPath()}, new String[0]);
    }

    public void testUUID() throws RepositoryException {
        Node n1 = testRootNode.addNode(nodeName1);
        if (!n1.isNodeType(mixReferenceable)) {
            n1.addMixin(mixReferenceable);
        }
        superuser.save();

        Node n2 = n1.addNode(nodeName2);

        journal = obsMgr.getEventJournal();
        journal.skipTo(System.currentTimeMillis());

        superuser.save();

        checkJournal(new String[]{n2.getPath()}, new String[0]);
    }

    public void testUserData() throws RepositoryException {
        testRootNode.addNode(nodeName1);
        String data = createRandomString(5);
        obsMgr.setUserData(data);

        journal = obsMgr.getEventJournal();
        journal.skipTo(System.currentTimeMillis());

        superuser.save();

        assertTrue("no more events", journal.hasNext());
        assertEquals("Wrong user data", data, journal.nextEvent().getUserData());
    }

    //-------------------------------< internal >-------------------------------

    /**
     * Checks the journal for events.
     *
     * @param allowed allowed paths for the returned events.
     * @param denied denied paths for the returned events.
     * @throws RepositoryException if an error occurs while reading the event
     *          journal.
     */
    private void checkJournal(String[] allowed, String[] denied) throws RepositoryException {
        Set allowedSet = new HashSet(Arrays.asList(allowed));
        Set deniedSet = new HashSet(Arrays.asList(denied));
        while (journal.hasNext()) {
            String path = journal.nextEvent().getPath();
            allowedSet.remove(path);
            if (deniedSet.contains(path)) {
                fail(path + " must not be present in journal");
            }
        }
        assertTrue("Missing paths in journal: " + allowedSet, allowedSet.isEmpty());
    }
}
