package com.xhhao.insight.storage.database;

import com.xhhao.insight.storage.StorageException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Keeps the Xerial driver registered while storage instances from this plugin are active.
 */
final class SqliteDriverLease {

    private static final Object MONITOR = new Object();
    private static final String DRIVER_CLASS = "org.sqlite.JDBC";
    private static int leaseCount;

    private boolean acquired;

    void acquire() {
        synchronized (MONITOR) {
            if (acquired) {
                return;
            }
            if (leaseCount == 0) {
                try {
                    Class<?> driverType = Class.forName(
                        DRIVER_CLASS, true, SqliteDriverLease.class.getClassLoader());
                    if (!hasOwnedDriver()) {
                        Driver driver = (Driver) driverType.getDeclaredConstructor().newInstance();
                        DriverManager.registerDriver(driver);
                    }
                } catch (ReflectiveOperationException | SQLException e) {
                    throw new StorageException("Xerial SQLite JDBC driver is not available", e);
                }
            }
            leaseCount++;
            acquired = true;
        }
    }

    void release() {
        synchronized (MONITOR) {
            if (!acquired) {
                return;
            }
            acquired = false;
            leaseCount--;
            if (leaseCount != 0) {
                return;
            }

            List<Driver> ownedDrivers = new ArrayList<>();
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                Driver driver = drivers.nextElement();
                if (DRIVER_CLASS.equals(driver.getClass().getName())
                    && driver.getClass().getClassLoader() == SqliteDriverLease.class.getClassLoader()) {
                    ownedDrivers.add(driver);
                }
            }
            for (Driver driver : ownedDrivers) {
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException ignored) {
                    // The class loader is being released; no useful recovery is possible here.
                }
            }
        }
    }

    private static boolean hasOwnedDriver() {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (DRIVER_CLASS.equals(driver.getClass().getName())
                && driver.getClass().getClassLoader() == SqliteDriverLease.class.getClassLoader()) {
                return true;
            }
        }
        return false;
    }
}
