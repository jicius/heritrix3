/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.archive.modules.recrawl.hbase;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HConnection;
import org.apache.hadoop.hbase.client.HConnectionManager;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;

/**
 * @author kenji
 * @author nlevitt
 */
public class HBaseTable extends HBaseTableBean {

    static final Logger logger =
            Logger.getLogger(HBaseTable.class.getName());

    protected boolean create = false;
    protected ThreadLocal<HConnection> hconn = new ThreadLocal<HConnection>();
    protected ThreadLocal<HTableInterface> htable = new ThreadLocal<HTableInterface>();

    public boolean getCreate() {
        return create;
    }
    /** Create the named table if it doesn't exist. */
    public void setCreate(boolean create) {
        this.create = create;
    }

    public HBaseTable() {
    }

    protected HConnection hconnection() throws IOException {
        if (hconn.get() == null) {
            hconn.set(HConnectionManager.createConnection(hbase.configuration()));
        }
        return hconn.get();
    }

    protected HTableInterface htable() throws IOException {
        if (htable.get() == null) {
            htable.set(hconnection().getTable(htableName));
        }
        return htable.get();
    }

    @Override
    public void put(Put p) throws IOException {
        try {
            htable().put(p);
        } catch (IOException e) {
            reset();
            throw e;
        }
    }

    @Override
    public Result get(Get g) throws IOException {
        try {
            return htable().get(g);
        } catch (IOException e) {
            reset();
            throw e;
        }
    }

    public HTableDescriptor getHtableDescriptor() throws IOException {
        try {
            return htable().getTableDescriptor();
        } catch (IOException e) {
            reset();
            throw e;
        }
    }

    @Override
    public void start() {
        int ATTEMPTS = 3;

        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                if (getCreate()) {
                    HBaseAdmin admin = hbase.admin();
                    if (!admin.tableExists(htableName)) {
                        HTableDescriptor desc = new HTableDescriptor(TableName.valueOf(htableName));
                        logger.info("hbase table '" + htableName + "' does not exist, creating it... " + desc);
                        admin.createTable(desc);
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "(attempt " + attempt + "/" + ATTEMPTS + ") problem creating hbase table " + htableName, e);
                if (attempt >= ATTEMPTS) {
                    throw new RuntimeException(e);
                }
            }
        }

        super.start();
    }

    protected void reset() {
        if (htable.get() != null) {
            try {
                htable.get().close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "htablename='" + htableName + "' htable.close() threw " + e, e);
            }
            htable.remove();
        }

        if (hconn.get() != null) {
            try {
                hconn.get().close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "hconn.close() threw " + e, e);
            }
            hconn.remove();
        }
    }

    @Override
    public synchronized void stop() {
        super.stop();
        reset();
    }
}
