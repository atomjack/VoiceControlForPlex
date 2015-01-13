package com.atomjack.vcfp;

import java.util.concurrent.Future;

public abstract class FutureRunnable implements Runnable {

	private Future<?> future;

	/* Getter and Setter for future */
	public Future getFuture() {
		return future;
	}

	public void setFuture(Future _future) {
		future = _future;
	}
}