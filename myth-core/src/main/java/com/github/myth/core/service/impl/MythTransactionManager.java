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
package com.github.myth.core.service.impl;


import com.github.myth.common.bean.context.MythTransactionContext;
import com.github.myth.common.bean.entity.MythParticipant;
import com.github.myth.common.bean.entity.MythTransaction;
import com.github.myth.common.enums.CoordinatorActionEnum;
import com.github.myth.common.enums.MythRoleEnum;
import com.github.myth.common.enums.MythStatusEnum;
import com.github.myth.common.utils.LogUtil;
import com.github.myth.core.concurrent.threadlocal.TransactionContextLocal;
import com.github.myth.core.coordinator.CoordinatorService;
import com.github.myth.core.coordinator.command.CoordinatorAction;
import com.github.myth.core.coordinator.command.CoordinatorCommand;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;


/**
 * @author xiaoyu
 */
@Component
@SuppressWarnings("unchecked")
public class MythTransactionManager {

    /**
     * logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MythTransactionManager.class);


    /**
     * 将事务信息存放在threadLocal里面
     */
    private static final ThreadLocal<MythTransaction> CURRENT = new ThreadLocal<>();

    private final CoordinatorService coordinatorService;


    private final CoordinatorCommand coordinatorCommand;


    @Autowired
    public MythTransactionManager(CoordinatorCommand coordinatorCommand,
                                  CoordinatorService coordinatorService) {
        this.coordinatorCommand = coordinatorCommand;
        this.coordinatorService = coordinatorService;

    }


    public MythTransaction begin(ProceedingJoinPoint point) {
        LogUtil.debug(LOGGER, () -> "开始执行Myth分布式事务！start");
        //第一次进入@Myth切面 事务为null
        MythTransaction mythTransaction = getCurrentTransaction();
        if (Objects.isNull(mythTransaction)) {

            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();
//           order @Myth的 clazz = class com.github.myth.demo.dubbo.order.service.impl.PaymentServiceImpl
            Class<?> clazz = point.getTarget().getClass();
//            新增并封装分布式事务消息（MythTransaction），会通过this.transId = IdWorkerUtils.getInstance().createUUID();产生新的事务id
            mythTransaction = new MythTransaction();
//            事务状态设置为开始
            mythTransaction.setStatus(MythStatusEnum.BEGIN.getCode());
//            事务角色设置为发起者
            mythTransaction.setRole(MythRoleEnum.START.getCode());
            //获取使用@Myth注解的类全名和方法名
            mythTransaction.setTargetClass(clazz.getName());
            mythTransaction.setTargetMethod(method.getName());
        }
        //保存当前事务信息，并通过线程池 MythTransactionThreadPool 消费消息队列 QUEUE进行 持久化到数据库
        coordinatorCommand.execute(new CoordinatorAction(CoordinatorActionEnum.SAVE, mythTransaction));

        //当前事务保存到ThreadLocal
        CURRENT.set(mythTransaction);

    //设置tcc事务上下文，这个类会传递给远端
        MythTransactionContext context = new MythTransactionContext();

        //设置事务id
        context.setTransId(mythTransaction.getTransId());

        //设置为发起者角色
        context.setRole(MythRoleEnum.START.getCode());

        //往ThreadLocal里设置 事务上下文 MythTransaction
        TransactionContextLocal.getInstance().set(context);

        return mythTransaction;

    }


    public MythTransaction actorTransaction(ProceedingJoinPoint point, MythTransactionContext mythTransactionContext) {
        MythTransaction mythTransaction =
                buildProviderTransaction(point, mythTransactionContext.getTransId(), MythStatusEnum.BEGIN.getCode());
        //保存创建的事务信息
        coordinatorCommand.execute(new CoordinatorAction(CoordinatorActionEnum.SAVE, mythTransaction));

        //设置提供者角色
        mythTransactionContext.setRole(MythRoleEnum.PROVIDER.getCode());

        TransactionContextLocal.getInstance().set(mythTransactionContext);

        return mythTransaction;
    }


    public void commitLocalTransaction(ProceedingJoinPoint point, String transId) {

        MythTransaction mythTransaction;
        if (StringUtils.isNoneBlank(transId)) {
            mythTransaction = coordinatorService.findByTransId(transId);
            if (Objects.nonNull(mythTransaction)) {
                updateStatus(transId, MythStatusEnum.COMMIT.getCode());
            } else {
                mythTransaction = buildProviderTransaction(point, transId, MythStatusEnum.COMMIT.getCode());
                //保存当前事务信息
                coordinatorCommand.execute(new CoordinatorAction(CoordinatorActionEnum.SAVE, mythTransaction));

            }
        }

    }





    public void sendMessage() {
        MythTransaction mythTransaction = getCurrentTransaction();
        if (Objects.nonNull(mythTransaction)) {
            coordinatorService.sendMessage(mythTransaction);
        }
    }


    public boolean isBegin() {
        return CURRENT.get() != null;
    }


    public void cleanThreadLocal() {
        CURRENT.remove();
    }


    public MythTransaction getCurrentTransaction() {
        return CURRENT.get();
    }


    public void updateStatus(String transId, Integer status) {
        coordinatorService.updateStatus(transId, status);
    }

    public void registerParticipant(MythParticipant participant) {
        final MythTransaction transaction = this.getCurrentTransaction();
        transaction.registerParticipant(participant);
//        更新 List<MythParticipant>  只更新这一个字段数据
        coordinatorService.updateParticipant(transaction);
    }

    private MythTransaction buildProviderTransaction(ProceedingJoinPoint point, String transId, Integer status) {
        MythTransaction mythTransaction = new MythTransaction(transId);

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();

        Class<?> clazz = point.getTarget().getClass();

        mythTransaction.setStatus(status);
        mythTransaction.setRole(MythRoleEnum.PROVIDER.getCode());
        mythTransaction.setTargetClass(clazz.getName());
        mythTransaction.setTargetMethod(method.getName());
        return mythTransaction;
    }
}
