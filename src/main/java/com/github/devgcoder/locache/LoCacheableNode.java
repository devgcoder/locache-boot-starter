package com.github.devgcoder.locache;

/**
 * @author duheng
 * @Date 2021/11/19 17:05
 */
public class LoCacheableNode {

	private long expireTime;

	private Object nodeVal;


	public LoCacheableNode(long expireTime, Object nodeVal) {
		this.expireTime = expireTime;
		this.nodeVal = nodeVal;
	}

	public long getExpireTime() {
		return expireTime;
	}

	public void setExpireTime(long expireTime) {
		this.expireTime = expireTime;
	}

	public Object getNodeVal() {
		return nodeVal;
	}

	public void setNodeVal(Object nodeVal) {
		this.nodeVal = nodeVal;
	}
}
