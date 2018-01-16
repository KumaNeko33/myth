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

package com.github.myth.dubbo.filter;


import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcResult;
import com.github.myth.annotation.MessageTypeEnum;
import com.github.myth.annotation.Myth;
import com.github.myth.common.bean.context.MythTransactionContext;
import com.github.myth.common.bean.entity.MythInvocation;
import com.github.myth.common.bean.entity.MythParticipant;
import com.github.myth.common.constant.CommonConstant;
import com.github.myth.common.exception.MythRuntimeException;
import com.github.myth.common.utils.GsonUtils;
import com.github.myth.core.concurrent.threadlocal.TransactionContextLocal;
import com.github.myth.core.service.impl.MythTransactionManager;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 采用rpc框架的某些特性来帮助我们获取到 @Myth注解信息
 * @author xiaoyu
 */
@Activate(group = {Constants.SERVER_KEY, Constants.CONSUMER})
public class DubboMythTransactionFilter implements Filter {

    private MythTransactionManager mythTransactionManager;

    public void setMythTransactionManager(MythTransactionManager mythTransactionManager) {
        this.mythTransactionManager = mythTransactionManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {

        String methodName = invocation.getMethodName();
        Class clazz = invoker.getInterface();
        Class[] args = invocation.getParameterTypes();
        final Object[] arguments = invocation.getArguments();

        Method method = null;
        Myth myth = null;
        try {
            method = clazz.getDeclaredMethod(methodName, args);
            myth = method.getAnnotation(Myth.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        //只有方法上打了@Myth注解的(即myth != null)才会进入后续逻辑，否则直接执行返回
        if (Objects.nonNull(myth)) {
            try {
                //这里ThreadLocal里已经设置了 之前在Order的makePayment的@Myth注解切面中 新增的 事务 MythTransaction,同一个线程共享这个变量
                final MythTransactionContext mythTransactionContext =
                        TransactionContextLocal.getInstance().get();
                if (Objects.nonNull(mythTransactionContext)) {
//                    服务器消费方使用RpcContext.getContext().setAttachment(); 传递参数。服务提供方使用RpcContext.getContext().getAttachments();获取参数
//                    RpcContext是一个ThreadLocal的临时状态记录器，当接收到RPC请求，或发起RPC请求时，RpcContext的状态都会变化。比如A调用B，B再调用C，则B机器上，
//                  在B调用C之前，RpcContext记录的是A调用B的信息，在B调用C之后，RpcContext记录的是B调用C。
//                    客户端：RpcContext.getContext().setAttachment("sourceid", "15700007");--A
//                    调dubbo接口：smsService.smsSend();
//                    服务端：RpcContext.getContext().getAttachment("sourceid");--B
//                    一定要注意，调接口时，必须是A直接到B，如果A没有直接到B，而是先到C，再由C到B，那么在B里getAttachment()，就获取不到值了
                    RpcContext.getContext()
                            .setAttachment(CommonConstant.MYTH_TRANSACTION_CONTEXT,
                                    GsonUtils.getInstance().toJson(mythTransactionContext));
                }
                //封装调用点,事务参与者
                final MythParticipant participant =
                        buildParticipant(mythTransactionContext, myth,
                                method, clazz, arguments, args);
                if (Objects.nonNull(participant)) {
                    //根据配置保存到数据库
                    mythTransactionManager.registerParticipant(participant);
                }
                return invoker.invoke(invocation);

            } catch (RpcException e) {
                e.printStackTrace();
                return new RpcResult();
            }
        } else {
//            否则直接执行返回
            return invoker.invoke(invocation);
        }

    }

    private MythParticipant buildParticipant(MythTransactionContext mythTransactionContext,
                                             Myth myth, Method method,
                                             Class clazz, Object[] arguments, Class... args)
            throws MythRuntimeException {

        if (Objects.nonNull(mythTransactionContext)) {

            MythInvocation mythInvocation = new MythInvocation(clazz,
                    method.getName(),
                    args, arguments);
            //事务消息目的地，account或inventory
            final String destination = myth.destination();
            //默认点对点
            final Integer pattern = myth.pattern().getCode();


            //封装调用点,事务参与者
            return new MythParticipant(
                    mythTransactionContext.getTransId(),
                    destination,
                    pattern,
                    mythInvocation);

        }

        return null;


    }
}
