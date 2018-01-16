/*
 *
 * Copyright 2017-2018 549477611@qq.com(xiaoyu)
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.github.myth.core.concurrent.threadlocal;


import com.github.myth.common.bean.context.MythTransactionContext;

/**
 * @author xiaoyu
 * 多线程编程
 * 当使用ThreadLocal维护变量时，ThreadLocal为每个使用该变量的线程提供独立的变量副本，所以每一个线程都可以独立地改变自己的副本，而不会影响其它线程所对应的副本。
 * 从线程的角度看，目标变量就象是线程的本地变量，这也是类名中“Local”所要表达的意思。
 */
public class TransactionContextLocal {

//    ThreadLocal是如何做到为每一个线程维护变量的副本的呢？其实实现的思路很简单：在ThreadLocal类中有一个Map，用于存储每一个线程的变量副本，
// Map中元素的键为线程对象，而值对应线程的变量副本。
    private static final ThreadLocal<MythTransactionContext> CURRENT_LOCAL = new ThreadLocal<>();

    private static final TransactionContextLocal TRANSACTION_CONTEXT_LOCAL = new TransactionContextLocal();

    private TransactionContextLocal() {

    }

    public static TransactionContextLocal getInstance() {
        return TRANSACTION_CONTEXT_LOCAL;
    }

//    设置当前线程的线程局部变量的值。
    public void set(MythTransactionContext context) {
        CURRENT_LOCAL.set(context);
    }

//    返回当前线程所对应的线程局部变量。
    public MythTransactionContext get() {
        return CURRENT_LOCAL.get();
    }

//    将当前线程局部变量的值删除，目的是为了减少内存的占用
    public void remove() {
        CURRENT_LOCAL.remove();
    }
}
