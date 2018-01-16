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

package com.github.myth.demo.dubbo.order.service.impl;


import com.github.myth.annotation.Myth;
import com.github.myth.common.exception.MythRuntimeException;
import com.github.myth.demo.dubbo.account.api.dto.AccountDTO;
import com.github.myth.demo.dubbo.account.api.entity.AccountDO;
import com.github.myth.demo.dubbo.account.api.service.AccountService;
import com.github.myth.demo.dubbo.inventory.api.dto.InventoryDTO;
import com.github.myth.demo.dubbo.inventory.api.entity.Inventory;
import com.github.myth.demo.dubbo.inventory.api.service.InventoryService;
import com.github.myth.demo.dubbo.order.mapper.OrderMapper;
import com.github.myth.demo.dubbo.order.entity.Order;
import com.github.myth.demo.dubbo.order.enums.OrderStatusEnum;
import com.github.myth.demo.dubbo.order.service.PaymentService;
import com.github.myth.dubbo.filter.DubboMythTransactionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author xiaoyu
 */
@Service
public class PaymentServiceImpl implements PaymentService {


    /**
     * logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentServiceImpl.class);


    private final OrderMapper orderMapper;

    private final AccountService accountService;

    private final InventoryService inventoryService;

    private static final String SUCCESS = "success";

    @Autowired(required = false)
    public PaymentServiceImpl(OrderMapper orderMapper,
                              AccountService accountService,
                              InventoryService inventoryService) {
        this.orderMapper = orderMapper;
        this.accountService = accountService;
        this.inventoryService = inventoryService;
    }


    @Override
    @Myth(destination = "")//此时代码不会直接进入paymentService.makePayment方法，而是先进入切面 DubboMythTransactionInterceptor
    public void makePayment(Order order) {

        //做资金账户的检验工作 这里只是demo 。。。
        final AccountDO accountDO = accountService.findByUserId(order.getUserId());//在OrderServiceImpl中设置userId = 10000
        if (accountDO.getBalance().compareTo(order.getTotalAmount()) <= 0) {
//            return;
            throw new MythRuntimeException("余额不足！");
        }
        //做库存的检验工作
        final Inventory inventory = inventoryService.findByProductId(order.getProductId());//1,在OrderServiceImpl中设置,demo中的表里只有商品id为1的数据

        if (inventory.getTotalInventory() < order.getCount()) {
//            return;
            throw new MythRuntimeException("库存不足！");
        }

        order.setStatus(OrderStatusEnum.PAY_SUCCESS.getCode());
        orderMapper.update(order);
        //扣除用户余额
        AccountDTO accountDTO = new AccountDTO();
        accountDTO.setAmount(order.getTotalAmount());
        accountDTO.setUserId(order.getUserId());
//        springAop的特性，在接口上加注解，是无法进入切面的，所以我们在这里，要采用rpc框架的某些特性来帮助我们获取到 @Myth注解信息, 这一步很重要。
// 这里我们演示的是 dubbo，所以进入 myth-dubbo工程的 DubboMythTransactionFilter 。（其中 springcloud，与motan这部分实现分别对应：MythFeignHandler类 和
// MotanMythTransactionFilter 类， 都是通过框架自身过滤器特性来实现， 逻辑与 dubbo 一样，只是实现上有少许差别~ 童鞋们一看便知，这里不再赘述~）
        accountService.payment(accountDTO);
        //进入扣减库存操作
        InventoryDTO inventoryDTO = new InventoryDTO();
        inventoryDTO.setCount(order.getCount());
        inventoryDTO.setProductId(order.getProductId());
        inventoryService.decrease(inventoryDTO);
        LOGGER.debug("=============Myth分布式事务执行完成！=======");
    }

}
