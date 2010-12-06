/**
 * Copyright 2010 Rice University
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
 *
 * @author Scott A. Crosby <scrosby@cs.rice.edu>
 *
 */

package edu.rice.batchsig;

/** Represent a queue of messages to be signed with some sort of bulk signature mechanism. */
public interface ProcessQueue<T> {

	/** Add a message to be processed. Called concurrently. */
	public abstract void add(T message);

	/**
	 * Process all of the messages, signing every one. May be done in a separate
	 * signing thread. Called concurrently.
	 */
	abstract public void process();

	/**
	 * Indicate that all processing is done. Called concurrently to add().
	 */
	abstract public void finish();

	public abstract int peekSize();
}