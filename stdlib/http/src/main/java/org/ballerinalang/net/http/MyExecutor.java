package org.ballerinalang.net.http;

import org.ballerinalang.jvm.observability.ObservabilityConstants;
import org.ballerinalang.jvm.observability.ObserveUtils;
import org.ballerinalang.jvm.observability.ObserverContext;
import org.ballerinalang.jvm.scheduling.Scheduler;
import org.ballerinalang.jvm.scheduling.Strand;
import org.ballerinalang.jvm.types.BType;
import org.ballerinalang.jvm.types.BTypes;
import org.ballerinalang.jvm.types.BUnionType;
import org.ballerinalang.jvm.types.TypeFlags;
import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.jvm.values.connector.CallableUnitCallback;

import java.util.Map;
import java.util.function.Function;

public class MyExecutor {

    private static final BUnionType OPTIONAL_ERROR_TYPE = new BUnionType(
            new BType[] { BTypes.typeError, BTypes.typeNull },
            TypeFlags.asMask(TypeFlags.NILABLE, TypeFlags.PURETYPE));

    public static void submit(ObjectValue service, String resourceName,
                              CallableUnitCallback callback, Map<String, Object> properties, Object... args){

        try{
            //Function<Object[], Object> func = objects -> {
            Strand strand = new Strand(null);
            Scheduler.strandHolder.get().strand= strand;
//            if (ObserveUtils.isObservabilityEnabled() && properties != null &&
//                    properties.containsKey(ObservabilityConstants.KEY_OBSERVER_CONTEXT)) {
//                strand.observerContext =
//                        (ObserverContext) properties.remove(ObservabilityConstants.KEY_OBSERVER_CONTEXT);
//            }
            service.call(strand, resourceName, args);
            //};

//        Object res =func.apply(new Object[1]);
//
            callback.notifySuccess();
        }
        finally {
            Scheduler.strandHolder.get().strand= null;
        }



    }


    /**
     * This method will execute Ballerina resource in non-blocking manner. It will use Ballerina worker-pool for the
     * execution and will return the connector thread immediately.
     *
     * @param scheduler    available scheduler.
     * @param service      to be executed.
     * @param resourceName to be executed.
     * @param callback     to be executed when execution completes.
     * @param properties   to be passed to context.
     * @param args         required for the resource.
     */
    public static void submit(Scheduler scheduler, ObjectValue service, String resourceName,
                              CallableUnitCallback callback, Map<String, Object> properties, Object... args) {

        Function<Object[], Object> func = objects -> {
            Strand strand = (Strand) objects[0];
            if (ObserveUtils.isObservabilityEnabled() && properties != null &&
                    properties.containsKey(ObservabilityConstants.KEY_OBSERVER_CONTEXT)) {
                strand.observerContext =
                        (ObserverContext) properties.remove(ObservabilityConstants.KEY_OBSERVER_CONTEXT);
            }
            return service.call(strand, resourceName, args);
        };



        scheduler.schedule(new Object[1], func, null, callback, properties, OPTIONAL_ERROR_TYPE);
    }

}
