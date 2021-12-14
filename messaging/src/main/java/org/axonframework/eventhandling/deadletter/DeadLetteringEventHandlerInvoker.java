/*
 * Copyright (c) 2010-2021. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.deadletter;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.Segment;
import org.axonframework.messaging.deadletter.DeadLetterQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class DeadLetteringEventHandlerInvoker implements EventHandlerInvoker {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final EventHandlerInvoker delegate;
    private final DeadLetterQueue<EventMessage<?>> queue;
    private final String processingGroup;

    /**
     * @param builder
     */
    protected DeadLetteringEventHandlerInvoker(Builder builder) {
        builder.validate();
        this.delegate = builder.delegate;
        this.queue = builder.queue;
        this.processingGroup = builder.processingGroup;
    }

    /**
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean canHandle(EventMessage<?> eventMessage, Segment segment) {
        return delegate.canHandle(eventMessage, segment);
    }

    @Override
    public boolean canHandleType(Class<?> payloadType) {
        return delegate.canHandleType(payloadType);
    }

    @Override
    public void handle(EventMessage<?> message, Segment segment) throws Exception {
        String sequenceIdentifier = Integer.toString(delegate.sequenceIdentifier(message).hashCode());
        if (queue.enqueueIfPresent(sequenceIdentifier, processingGroup, message)) {
            logger.info(
                    "Event [{}] is added to the dead-letter queue since its processing id [{}-{}] was already present.",
                    message, sequenceIdentifier, processingGroup
            );
        } else {
            logger.debug("Event [{}] with processing id [{}-{}] is not present in the dead-letter queue present."
                                 + "Handle operation is delegated to the wrapped EventHandlerInvoker.",
                         message, sequenceIdentifier, processingGroup);
            try {
                // TODO: 03-12-21 how to deal with the delegates ListenerInvocationErrorHandler in this case?
                //  It is mandatory to rethrow the exception, as otherwise the message isn't enqueued.
                delegate.handle(message, segment);
            } catch (Exception e) {
                queue.enqueue(sequenceIdentifier, processingGroup, message, e);
            }
        }
    }

    @Override
    public boolean supportsReset() {
        return delegate.supportsReset();
    }

    @Override
    public void performReset() {
        delegate.performReset();
    }

    @Override
    public <R> void performReset(R resetContext) {
        delegate.performReset(resetContext);
    }

    @Override
    public Object sequenceIdentifier(EventMessage<?> event) {
        return delegate.sequenceIdentifier(event);
    }

    /**
     *
     */
    public static class Builder {

        private EventHandlerInvoker delegate;
        private DeadLetterQueue<EventMessage<?>> queue;
        private String processingGroup;

        /**
         * @param delegate
         * @return
         */
        public Builder delegate(EventHandlerInvoker delegate) {
            assertNonNull(delegate, "The delegate EventHandlerInvoker may not be null");
            this.delegate = delegate;
            return this;
        }

        /**
         * @param queue
         * @return
         */
        public Builder queue(DeadLetterQueue<EventMessage<?>> queue) {
            assertNonNull(queue, "The DeadLetterQueue may not be null");
            this.queue = queue;
            return this;
        }

        /**
         * Sets the name of this invoker.
         *
         * @param processingGroup the name of this {@link EventHandlerInvoker}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder processingGroup(String processingGroup) {
            assertNonEmpty(processingGroup, "The processing group may not be null or empty");
            this.processingGroup = processingGroup;
            return this;
        }

        /**
         * @return
         */
        public DeadLetteringEventHandlerInvoker build() {
            return new DeadLetteringEventHandlerInvoker(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonNull(delegate, "The delegate EventHandlerInvoker is a hard requirement and should be provided");
            assertNonNull(queue, "The DeadLetterQueue is a hard requirement and should be provided");
            assertNonNull(processingGroup, "The processing group is a hard requirement and should be provided");
        }
    }
}