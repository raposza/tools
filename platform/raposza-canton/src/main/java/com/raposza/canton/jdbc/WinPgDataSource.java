// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.jdbc;

import org.postgresql.ds.PGSimpleDataSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A PostgreSQL DataSource that will not ask a Windows postmaster for something
 * it is not allowed to give.
 *
 * <h2>What it rewrites, and why only this</h2>
 *
 * `client_connection_check_interval` polls the socket for a close event, and
 * PostgreSQL implements it only where the kernel exposes that - Linux, macOS,
 * illumos and the BSDs. On Windows the postmaster accepts 0 and refuses every
 * other value, on any build from any packager:
 *
 * <pre>
 * ERROR:  invalid value for parameter "client_connection_check_interval": 5000
 * DETAIL:  client_connection_check_interval must be set to 0 on platforms that
 *          lack POLLRDHUP.
 * </pre>
 *
 * Every Canton node opens a locked connection for schema migration and sends
 * 5s on it from a constructor default that NO configuration path reaches: the
 * node's `replication.connection-pool` and the sequencer's `high-availability`
 * were both set to 0s on Windows, accepted, and changed nothing. The pool then
 * fails on that statement, retries with backoff, and the node never comes up.
 * So the statement is corrected here instead, on its way out.
 *
 * ONLY THE VALUE OF THAT ONE PARAMETER MOVES. Every other statement is passed
 * through byte for byte, and a statement that does not mention the parameter is
 * not even copied.
 *
 * <h2>What it costs</h2>
 *
 * Canton believes the migration lock's connection carries a 5s client check; it
 * carries none. On Windows it could not have carried one whatever was
 * configured. The postmaster therefore notices a dead migration-lock client at
 * the next socket interaction rather than within 5s, which on a single-machine
 * developer sandbox is nothing.
 *
 * <h2>How Canton reaches it</h2>
 *
 * By name, in the `dataSourceClass` of every storage block, and it is loaded
 * from the directory {@link PgShim} extracts it to. A Canton launched with
 * `-jar` cannot see it at all - `-jar` IGNORES `-cp` - which is why the
 * launchers name the jar's own main class on Windows instead.
 *
 * Author Claude/bentzn
 */
public final class WinPgDataSource extends PGSimpleDataSource {

    private static final long serialVersionUID = 1L;

    /**
     * The parameter, an equals or the SQL keyword TO, then a bare number with
     * an optional unit or the same quoted. The value is group 2 and is the only
     * thing replaced.
     */
    private static final Pattern PAT_VALUE = Pattern.compile(
            "(?i)(client_connection_check_interval\\s*(?:=|\\bto\\b)\\s*)('[^']*'|[0-9]+[a-z]*)");

    private static final String[] ARR_METHOD_SQL = { "execute", "executeQuery", "executeUpdate",
            "executeLargeUpdate", "addBatch" };

    private static volatile boolean flagSaid;


    /**
     * @param strSql a statement on its way to the server
     * @return the same statement with the interval set to 0, or the same
     *         reference when it does not mention the parameter
     */
    public static String strRewrite(String strSql) {
        if (strSql == null)
            return null;
        Matcher matcher = PAT_VALUE.matcher(strSql);
        if (!matcher.find())
            return strSql;
        matcher.reset();
        StringBuilder bld = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(bld, Matcher.quoteReplacement(matcher.group(1) + "0"));
        }
        matcher.appendTail(bld);
        String strOut = bld.toString();
        if (!flagSaid) {
            flagSaid = true;
            System.out.println("[winpg] client_connection_check_interval rewritten to 0: "
                    + strOut.trim());
        }
        return strOut;
    }


    @Override
    public Connection getConnection() throws SQLException {
        return connWrap(super.getConnection());
    }


    @Override
    public Connection getConnection(String strUser, String strPassword) throws SQLException {
        return connWrap(super.getConnection(strUser, strPassword));
    }


    private static Connection connWrap(Connection conn) {
        return (Connection) Proxy.newProxyInstance(WinPgDataSource.class.getClassLoader(),
                new Class<?>[] { Connection.class }, new HandlerCall(conn, true));
    }


    /**
     * One handler for both levels. On a Connection it rewrites the sql handed
     * to prepareStatement and prepareCall and wraps whatever Statement comes
     * back; on a Statement it rewrites the sql handed to the execute family.
     */
    private static final class HandlerCall implements InvocationHandler {

        private final Object objTarget;
        private final boolean flagConnection;


        private HandlerCall(Object objTarget, boolean flagConnection) {
            this.objTarget = objTarget;
            this.flagConnection = flagConnection;
        }


        @Override
        public Object invoke(Object objProxy, Method method, Object[] arrArg) throws Throwable {
            Object[] arrPass = arrArg;
            if (arrPass != null && arrPass.length > 0 && arrPass[0] instanceof String
                    && flagRewrites(method.getName())) {
                arrPass = arrPass.clone();
                arrPass[0] = strRewrite((String) arrPass[0]);
            }
            Object objOut;
            try {
                objOut = method.invoke(objTarget, arrPass);
            }
            catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
            if (!flagConnection || objOut == null)
                return objOut;
            Class<?> clsReturn = method.getReturnType();
            if (!java.sql.Statement.class.isAssignableFrom(clsReturn))
                return objOut;
            return Proxy.newProxyInstance(WinPgDataSource.class.getClassLoader(),
                    new Class<?>[] { clsReturn }, new HandlerCall(objOut, false));
        }


        private boolean flagRewrites(String strName) {
            if (flagConnection)
                return strName.startsWith("prepare");
            for (String strMethod : ARR_METHOD_SQL) {
                if (strMethod.equals(strName))
                    return true;
            }
            return false;
        }
    }
}
