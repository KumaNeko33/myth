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

package com.github.myth.dubbo.interceptor;

import com.alibaba.dubbo.rpc.RpcContext;
import com.github.myth.common.bean.context.MythTransactionContext;
import com.github.myth.common.constant.CommonConstant;
import com.github.myth.common.utils.GsonUtils;
import com.github.myth.core.concurrent.threadlocal.TransactionContextLocal;
import com.github.myth.core.interceptor.MythTransactionInterceptor;
import com.github.myth.core.service.MythTransactionAspectService;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author xiaoyu
 */
@Component
public class DubboMythTransactionInterceptor implements MythTransactionInterceptor {

    private final MythTransactionAspectService mythTransactionAspectService;

    @Autowired
    public DubboMythTransactionInterceptor(MythTransactionAspectService mythTransactionAspectService) {
        this.mythTransactionAspectService = mythTransactionAspectService;
    }

//    这里也是实现分布式事务的最关键一部分，通过同一个事务上下文来关联多子系统之间事务关系，是分布式事务实现的核心所在。
    @Override
    public Object interceptor(ProceedingJoinPoint pjp) throws Throwable {
        //第一次进入切面，RpcContext 中的MYTH_TRANSACTION_CONTEXT context为null
        final String context = RpcContext.getContext().getAttachment(CommonConstant.MYTH_TRANSACTION_CONTEXT);
        MythTransactionContext mythTransactionContext;
        if (StringUtils.isNoneBlank(context)) {
            //context不为空
            mythTransactionContext =
                    GsonUtils.getInstance().fromJson(context, MythTransactionContext.class);
        }else{
            mythTransactionContext= TransactionContextLocal.getInstance().get();//返回new ThreadLocal<>().get()
        }
//        因为第一次进来这些变量都没有值，所以我们会直接进入mythTransactionAspectService.invoke(mythTransactionContext, pjp)， 此时mythTransactionContext为null，
        return mythTransactionAspectService.invoke(mythTransactionContext, pjp);
    }
}
