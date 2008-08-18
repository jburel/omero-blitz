/*
 *   $Id$
 *
 *   Copyright 2008 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.services.throttling;

import ome.api.ServiceInterface;
import ome.services.blitz.util.IceMethodInvoker;
import ome.services.blitz.util.ServantHelper;
import Ice.Current;

/**
 * Throttling implementation which only allows a single invocation to be run at
 * any given time.
 * 
 */
public class SerialThrottlingStrategy implements ThrottlingStrategy {

    private final Slot slot;

    private final Queue queue;

    public SerialThrottlingStrategy() {
        queue = new Queue();
        slot = new Slot(queue);
    }

    public void serviceInterfaceCall(ServiceInterface service,
            IceMethodInvoker invoker, ServantHelper helper, Object __cb,
            Ice.Current __current, Object... args) {
        Callback cb = new Callback(service, invoker, helper, __cb, __current,
                args);
        queue.put(cb);
    }

    public void runnableCall(Current __current, Task task) {
        throw new UnsupportedOperationException();
    }

}
