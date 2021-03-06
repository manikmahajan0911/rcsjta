/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010-2016 Orange.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package android.tests.provider;

import com.gsma.services.rcs.RcsService;
import com.gsma.services.rcs.filetransfer.FileTransfer;
import com.gsma.services.rcs.filetransfer.FileTransferLog;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.test.InstrumentationTestCase;

public class FileTransferLogTest extends InstrumentationTestCase {

    // @formatter:off
    private final String[] FILE_TRANSFER_LOG_PROJECTION = new String[] {
            FileTransferLog.BASECOLUMN_ID, 
            FileTransferLog.FT_ID, 
            FileTransferLog.CHAT_ID,
            FileTransferLog.CONTACT, 
            FileTransferLog.FILE, 
            FileTransferLog.FILENAME,
            FileTransferLog.MIME_TYPE, 
            FileTransferLog.FILEICON,
            FileTransferLog.FILEICON_MIME_TYPE, 
            FileTransferLog.DIRECTION,
            FileTransferLog.FILESIZE,
            FileTransferLog.TRANSFERRED, 
            FileTransferLog.TIMESTAMP,
            FileTransferLog.TIMESTAMP_SENT, 
            FileTransferLog.TIMESTAMP_DELIVERED,
            FileTransferLog.TIMESTAMP_DISPLAYED, 
            FileTransferLog.STATE,
            FileTransferLog.REASON_CODE, 
            FileTransferLog.READ_STATUS,
            FileTransferLog.FILE_EXPIRATION, 
            FileTransferLog.FILEICON_EXPIRATION,
            FileTransferLog.EXPIRED_DELIVERY,
            FileTransferLog.DISPOSITION
    };
    // @formatter:on

    private ContentProviderClient mProvider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mProvider = getInstrumentation().getTargetContext().getContentResolver()
                .acquireContentProviderClient(FileTransferLog.CONTENT_URI);
        assertNotNull(mProvider);
    }

    /**
     * Test the FileTransferLog according to GSMA API specifications.<br>
     * Check the following operations:
     * <ul>
     * <li>query
     * <li>insert
     * <li>delete
     * <li>update
     */
    public void testFileTransferLogQuery() throws RemoteException {
        // Check that provider handles columns names and query operation
        Cursor cursor = null;
        try {
            String where = FileTransferLog.FT_ID.concat("=?");
            String[] whereArgs = new String[] {
                "0123456789"
            };
            cursor = mProvider.query(FileTransferLog.CONTENT_URI, FILE_TRANSFER_LOG_PROJECTION,
                    where, whereArgs, null);
            assertNotNull(cursor);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void testFileTransferLogQueryById() throws RemoteException {
        // Check that provider handles columns names and query operation by ID
        Uri uri = Uri.withAppendedPath(FileTransferLog.CONTENT_URI, "0123456789");
        // Check that provider handles columns names and query operation
        Cursor cursor = null;
        try {
            cursor = mProvider.query(uri, FILE_TRANSFER_LOG_PROJECTION, null, null, null);
            assertNotNull(cursor);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void testFileTransferLogQueryWithoutWhereClause() throws RemoteException {
        Cursor cursor = null;
        try {
            cursor = mProvider.query(FileTransferLog.CONTENT_URI, null, null, null, null);
            assertNotNull(cursor);
            Utils.checkProjection(FILE_TRANSFER_LOG_PROJECTION, cursor.getColumnNames());

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void testFileTransferLogInsert() {
        // // Check that provider does not support insert operation
        ContentValues values = new ContentValues();
        values.put(FileTransferLog.FILESIZE, 10000);
        values.put(FileTransferLog.FILE, "uri");
        values.put(FileTransferLog.FILENAME, "filename");
        values.put(FileTransferLog.DIRECTION, RcsService.Direction.INCOMING.toInt());
        values.put(FileTransferLog.FT_ID, "0123456789");
        values.put(FileTransferLog.MIME_TYPE, "image/jpeg");
        values.put(FileTransferLog.STATE, FileTransfer.State.INVITED.toInt());
        values.put(FileTransferLog.REASON_CODE, FileTransfer.ReasonCode.UNSPECIFIED.toInt());
        values.put(FileTransferLog.READ_STATUS, RcsService.ReadStatus.UNREAD.toInt());
        values.put(FileTransferLog.TIMESTAMP, System.currentTimeMillis());
        values.put(FileTransferLog.TIMESTAMP_DELIVERED, 0);
        values.put(FileTransferLog.TIMESTAMP_SENT, 0);
        values.put(FileTransferLog.TIMESTAMP_DISPLAYED, 0);
        values.put(FileTransferLog.TRANSFERRED, 0);
        values.put(FileTransferLog.CHAT_ID, "0102030405");

        try {
            mProvider.insert(FileTransferLog.CONTENT_URI, values);
            fail("FileTransferLog is read only");

        } catch (Exception ex) {
            assertTrue("insert into FileTransferLog should be forbidden",
                    ex instanceof RuntimeException);
        }
    }

    public void testFileTransferLogDelete() {
        // Check that provider supports delete operation
        try {
            mProvider.delete(FileTransferLog.CONTENT_URI, null, null);
            fail("FileTransferLog is read only");
        } catch (Exception e) {
            assertTrue("delete of FileTransferLog should be forbidden",
                    e instanceof RuntimeException);
        }
    }

    public void testFileTransferLogUpdate() {
        ContentValues values = new ContentValues();
        values.put(FileTransferLog.FILESIZE, 10000L);
        values.put(FileTransferLog.FILENAME, "filename");
        values.put(FileTransferLog.CONTACT, "+3360102030405");
        values.put(FileTransferLog.DIRECTION, RcsService.Direction.INCOMING.toInt());
        values.put(FileTransferLog.FT_ID, "ft_id");
        values.put(FileTransferLog.MIME_TYPE, "image/jpeg");
        values.put(FileTransferLog.STATE, FileTransfer.State.INVITED.toInt());
        values.put(FileTransferLog.TIMESTAMP, System.currentTimeMillis());
        values.put(FileTransferLog.TRANSFERRED, 0L);
        // Check that provider does not support update operation
        try {
            String where = FileTransferLog.FT_ID.concat("=?");
            String[] whereArgs = new String[] {
                "0123456789"
            };
            mProvider.update(FileTransferLog.CONTENT_URI, values, where, whereArgs);
            fail("FileTransferLog is read only");

        } catch (Exception ex) {
            assertTrue("update of FileTransferLog should be forbidden",
                    ex instanceof RuntimeException);
        }
    }

}
