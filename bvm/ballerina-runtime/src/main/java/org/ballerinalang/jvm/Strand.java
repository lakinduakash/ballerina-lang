/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.jvm;

import org.ballerinalang.jvm.values.ChannelDetails;
import org.ballerinalang.jvm.values.ErrorValue;
import org.ballerinalang.jvm.values.FutureValue;
import org.ballerinalang.jvm.values.MapValue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Strand base class used with jvm code generation for functions.
 *
 * @since 0.955.0
 */

public class Strand {
    public boolean yield;
    public Object[] frames;
    public int resumeIndex;
    public Future future;
    public boolean blocked;
    public List<Strand> blockedOn;
    public Scheduler scheduler;
    public Strand parent = null;
    public WDChannels wdChannels;
    public FlushDetail flushDetail;
    public boolean blockedOnExtern;
    public ChannelDetails[] channelDetails;
    private Map<String, Object> globalProps;

    public Strand(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.wdChannels = new WDChannels();
        this.blockedOn = new CopyOnWriteArrayList();
    }

    public Strand(Scheduler scheduler, Strand parent) {
        this.scheduler = scheduler;
        this.parent = parent;
        this.wdChannels = new WDChannels();
        this.blockedOn = new CopyOnWriteArrayList();
    }

    public Strand(Scheduler scheduler, Map<String, Object> properties) {
        this.scheduler = scheduler;
        this.globalProps = properties;
        this.wdChannels = new WDChannels();
        this.blockedOn = new CopyOnWriteArrayList();
    }

    public void handleChannelError(ChannelDetails[] channels, ErrorValue error) {
        for (int i = 0; i < channels.length; i++) {
            ChannelDetails channelDetails = channels[i];
            WorkerDataChannel channel = getWorkerDataChannel(channelDetails);

            if (channels[i].send) {
                channel.setSendError(error);
            } else {
                channel.setReceiveError(error);
            }
        }
    }

    public void setReturnValues(Object returnValue) {
        this.future = CompletableFuture.completedFuture(returnValue);
    }

    public Object getProperty(String key) {
        return this.globalProps.get(key);
    }

    public void setProperty(String key, Object value) {
        this.globalProps.put(key, value);
    }

    public ErrorValue handleFlush(ChannelDetails[] channels) {
        if (flushDetail == null) {
            this.flushDetail = new FlushDetail(channels);
        } else if (flushDetail.inProgress) {
            // this is a reschedule when flush is completed
            flushDetail.inProgress = false;
            return this.flushDetail.result;
        }

        for (int i = 0; i < channels.length; i++) {
            ErrorValue error = getWorkerDataChannel(channels[i]).flushChannel(this);
            if (error != null) {
                return error;
            } else if (this.flushDetail.flushedCount == this.flushDetail.flushChannels.length) {
                // flush completed
                return null;
            }
        }
        flushDetail.inProgress = true;
        this.yield = true;
        this.blocked = true;
        return null;
    }

    public void handleWaitMultiple(Map<String, FutureValue> keyValues, MapValue target) {
        this.blockedOn.clear();
        keyValues.entrySet().forEach(entry -> {
            synchronized (entry.getValue()) {
                if (entry.getValue().isDone) {
                    target.put(entry.getKey(), entry.getValue().result);
                } else {
                    this.yield = true;
                    this.blocked = true;
                    this.blockedOn.add(entry.getValue().strand);
                }
            }
        });
    }

    public WaitResult handleWaitAny(List<FutureValue> futures) {
        WaitResult waitResult = new WaitResult(false, null);
        for (FutureValue future : futures) {
            synchronized (future) {
                if (future.isDone) {
                    waitResult = new WaitResult(true, future.result);
                } else {
                    this.blockedOn.add(future.strand);
                }
            }
        }

        if (waitResult.done) {
            this.blockedOn.clear();
            for (FutureValue future : futures) {
                future.strand.scheduler.release(future.strand, this);
            }
        } else {
            this.yield = true;
            this.blocked = true;
        }

        return waitResult;
    }

    private WorkerDataChannel getWorkerDataChannel(ChannelDetails channel) {
        WorkerDataChannel dataChannel;
        if (channel.channelInSameStrand) {
            dataChannel = this.wdChannels.getWorkerDataChannel(channel.name);
        } else {
            dataChannel = this.parent.wdChannels.getWorkerDataChannel(channel.name);
        }
        return dataChannel;
    }

    /**
     * Class to hold flush action related details.
     *
     * 0.995.0
     */
    public static class FlushDetail {
        public ChannelDetails[] flushChannels;
        public int flushedCount;
        public Lock flushLock;
        public ErrorValue result;
        public boolean inProgress;

        public FlushDetail(ChannelDetails[] flushChannels) {
            this.flushChannels = flushChannels;
            this.flushedCount = 0;
            this.flushLock = new ReentrantLock();
            this.result = null;
            this.inProgress = false;
        }
    }

    /**
     * Holds both waiting state and result.
     *
     * 0.995.0
     */
    public static class WaitResult {
        public boolean done;
        public Object result;

        public WaitResult(boolean done, Object result) {
            this.done = done;
            this.result = result;
        }
    }
}
