/** 
 *   $Id$
 *
 *   Copyright 2008 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt 
 */
package ome.services.throttling;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** * Manages AMD-based method dispatches from blitz. * */
public class Queue {
    private final static Log log = LogFactory.getLog(Queue.class);

    interface Callback {
        void response(Object rv);

        void exception(Exception ex);

        Boolean ioIntensive();

        Boolean dbIntensive();
    }

    class CancelledException extends Exception {
    }

    private final BlockingQueue<Callback> q = new LinkedBlockingQueue<Callback>();
    private final AtomicBoolean done = new AtomicBoolean();

    public Queue() {
        done.set(false);
    }

    public void put(Callback callback) {
        boolean cont = !done.get();
        if (cont) {
            while (true) {
                try {
                    q.put(callback);
                    break;
                } catch (InterruptedException e) {
                    log.warn("Queue interrupted during put");
                }
            }
        } else {
            callback.exception(new CancelledException());
        }
    }

    public Callback take() {
        Callback cb = null;
        while (true) {
            try {
                cb = q.take();
            } catch (InterruptedException e) {
                log.warn("Queue interrupted during take");
            }
            break;
        }
        return cb;
    }

    public void destroy() {
        boolean wasDone = done.getAndSet(true);
        if (!wasDone) {
            for (Callback cb : q) {
                cb.exception(new CancelledException());
            }
        }
    }
}