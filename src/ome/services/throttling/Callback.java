/*
 *   $Id$
 *
 *   Copyright 2008 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.services.throttling;

import java.lang.reflect.Method;

import ome.api.ServiceInterface;
import ome.services.blitz.util.IceMethodInvoker;
import ome.services.blitz.util.ServantHelper;
import omero.InternalException;
import omero.util.IceMapper;

import org.springframework.util.Assert;

/**
 * Manages AMD-based method dispatches from blitz.
 * 
 */
public class Callback {

    private final Boolean io;
    private final Boolean db;
    private final IceMethodInvoker invoker;
    private final ServiceInterface service;
    private final ServantHelper helper;
    private final Object cb;
    private final Object[] args;
    private final Ice.Current current;

    private final Method response;
    private final Method exception;

    public Callback(Boolean io, Boolean db, ServiceInterface service,
            IceMethodInvoker invoker, ServantHelper helper, Object cb,
            Ice.Current current, Object... args) {

        Assert.notNull(helper, "Null servant helper");
        Assert.notNull(invoker, "Null invoker");
        Assert.notNull(service, "Null service");
        Assert.notNull(current, "Null current");
        Assert.notNull(cb, "Null callback");
        Assert.notNull(args, "Null argument array");

        this.io = io;
        this.db = db;
        this.cb = cb;
        this.helper = helper;
        this.service = service;
        this.invoker = invoker;
        this.current = current;
        this.args = args;

        response = getMethod(cb, "ice_response");
        exception = getMethod(cb, "ice_exception");

    }

    public Callback(ServiceInterface service, IceMethodInvoker invoker,
            ServantHelper helper, Object cb, Ice.Current current,
            Object... args) {
        this(null, null, service, invoker, helper, cb, current, args);
    }

    public void run() {
        IceMapper mapper = new IceMapper();
        try {
            Object retVal = invoker.invoke(service, current, mapper, args);
            helper.throwIfNecessary(retVal);
            response(retVal);
        } catch (Ice.UserException e) {
            exception(e);
        }
    }

    void response(Object rv) {
        try {
            if (invoker.isVoid(current)) {
                response.invoke(cb);
            } else {
                response.invoke(cb, rv);
            }
        } catch (Exception e) {
            try {
                InternalException ie = new InternalException();
                IceMapper.fillServerError(ie, e);
                ie.message = "Failed to invoke: " + this.toString();
                exception(ie);
            } catch (Exception e2) {
                throw new RuntimeException(
                        "Failed to invoke exception() after failed response()",
                        e2);
            }
        }
    }

    void exception(Exception ex) {
        try {
            exception.invoke(cb, ex);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke exception()", e);
        }
    }

    /**
     * Callback can be either IO-intensive ({@link Boolean#TRUE}),
     * IO-non-intensive ({@link Boolean#FALSE}), or it can be unknown ({@link <code>null</code>}).
     */
    Boolean ioIntensive() {
        return io;
    }

    /**
     * Callback can be either database-intensive ({@link Boolean#TRUE}),
     * database-non-intensive ({@link Boolean#FALSE}), or it can be unknown ({@link <code>null</code>}).
     */
    Boolean dbIntensive() {
        return db;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.append(" (");
        sb.append(cb);
        sb.append(" )");
        return sb.toString();
    }

    // Helpers
    // =========================================================================

    Method getMethod(Object o, String methodName) {
        Class c = getPublicInterface(o.getClass());
        Method[] methods = c.getMethods();
        Method rv = null;
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (methodName.equals(m.getName())) {
                if (rv != null) {
                    throw new RuntimeException(methodName + " exists twice!");
                } else {
                    rv = m;
                }
            }
        }
        return rv;
    }

    /**
     * The Ice AMD-implementations are package-private and so cannot be executed
     * on. Instead, we have to find the public interface and use its methods.
     */
    private Class getPublicInterface(Class c) {
        if (!c.getName().startsWith("AMD_")) {
            while (!c.equals(Object.class)) {
                Class[] ifaces = c.getInterfaces();
                for (Class c2 : ifaces) {
                    if (c2.getSimpleName().startsWith("AMD_")) {
                        return c2;
                    }
                }
                // Ok. We didn't find anything so recurse into the superclass
                c = c.getSuperclass();
            }
            throw new RuntimeException("No public AMD_ interface found.");
        } else {
            return c;
        }
    }
}
