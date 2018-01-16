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

import com.github.myth.common.config.MythConfig;
import com.github.myth.common.enums.RepositorySupportEnum;
import com.github.myth.common.enums.SerializeEnum;
import com.github.myth.common.serializer.ObjectSerializer;
import com.github.myth.common.utils.LogUtil;
import com.github.myth.common.utils.ServiceBootstrap;
import com.github.myth.core.coordinator.CoordinatorService;
import com.github.myth.core.helper.SpringBeanUtils;
import com.github.myth.core.service.MythInitService;
import com.github.myth.core.spi.CoordinatorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * <p>Description: .</p>
 *
 * @author xiaoyu(Myth)
 * @version 1.0
 * @date 2017/11/29 11:44
 * @since JDK 1.8
 */
@Service
public class MythInitServiceImpl implements MythInitService {

    /**
     * logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MythInitServiceImpl.class);

    private final CoordinatorService coordinatorService;

    @Autowired
    public MythInitServiceImpl(CoordinatorService coordinatorService) {
        this.coordinatorService = coordinatorService;
    }


    /**
     * Myth分布式事务初始化方法
     *
     * @param mythConfig TCC配置
     */
    @Override
    public void initialization(MythConfig mythConfig) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> LOGGER.error("系统关闭")));
        try {
            loadSpiSupport(mythConfig);
            coordinatorService.start(mythConfig);
        } catch (Exception ex) {
            LogUtil.error(LOGGER, "Myth事务初始化异常:{}", ex::getMessage);
            //非正常关闭
            System.exit(1);
        }
        LogUtil.info(LOGGER, () -> "Myth事务初始化成功！");
    }

    /**
     * 根据配置文件初始化spi
     *
     * @param mythConfig 配置信息
     */
    private void loadSpiSupport(MythConfig mythConfig) {

        //spi  serialize
        final SerializeEnum serializeEnum =
                SerializeEnum.acquire(mythConfig.getSerializer());//这里的 SerializeEnum的枚举值是 kryo
        //加载 ObjectSerializer 接口的所有实现类，包括 KryoSerializer
        final ServiceLoader<ObjectSerializer> objectSerializers =
                ServiceBootstrap.loadAll(ObjectSerializer.class);
        //遍历筛选出配置文件 mythConfig 配置的 ObjectSerializer实现类，即kryo 对应的 KryoSerializer
        final Optional<ObjectSerializer> serializer =
                StreamSupport.stream(objectSerializers.spliterator(),
                        true)
                        .filter(objectSerializer ->
                                Objects.equals(objectSerializer.getScheme(),
                                        serializeEnum.getSerialize())).findFirst();// SerializeEnum的枚举值是 kryo。而Stream.findFirst()  将Stream转成Optional对象
//                Stream.of(objectSerializers.spliterator()).filter(objectSerializer -> Objects.equals(objectSerializer.getScheme(),serializeEnum.getSerialize())).findFirst();
//        ifPresent：
//        如果Optional实例有值则为其调用consumer，否则不做处理
//        要理解ifPresent方法，首先需要了解Consumer类。简答地说，Consumer类包含一个抽象方法。该抽象方法对传入的值进行处理，但没有返回值。Java8支持不用接口直接通过lambda表达式传入参数。
//        如果Optional实例有值，调用ifPresent()可以接受接口段或lambda表达式。类似下面的代码：
//ifPresent方法接受lambda表达式作为参数。
//lambda表达式对 Optional的值（下面Optional是serializer,它的值是ObjectSerializer对象，即泛型对应的对象） 调用consumer进行处理（即下面的SpringBeanUtils.getInstance().registerBean(ObjectSerializer.class.getName(), s)）。
//这个consumer的作用是对传入的值进行处理，但没有返回值，这里：是将前面创建的 KryoSerializer 对象注入spring容器中
        serializer.ifPresent(coordinatorService::setSerializer);//设置 MessageEntity 消息对象的序列化方式为 前面创建的 KryoSerializer 对象，便于消息传播中 输入 输出
        serializer.ifPresent(s-> SpringBeanUtils.getInstance().registerBean(ObjectSerializer.class.getName(), s));//将前面创建的 KryoSerializer 字节码注入spring容器中


        //spi  repository support
        //配置事务的存储方式
        final RepositorySupportEnum repositorySupportEnum =
                RepositorySupportEnum.acquire(mythConfig.getRepositorySupport());//即DB
        final ServiceLoader<CoordinatorRepository> recoverRepositories =
                ServiceBootstrap.loadAll(CoordinatorRepository.class);


        final Optional<CoordinatorRepository> repositoryOptional =
                StreamSupport.stream(recoverRepositories.spliterator(), false)
                        .filter(recoverRepository ->
                                Objects.equals(recoverRepository.getScheme(),
                                        repositorySupportEnum.getSupport())).findFirst();//得到空间名scheme 等于DB的RecoverRepository即 JdbcCoordinatorRepository

        //将CoordinatorRepository实现注入到spring容器
        repositoryOptional.ifPresent(repository -> {
            serializer.ifPresent(repository::setSerializer);//设置 MythTransaction 的数据库持久化的序列化方式
            SpringBeanUtils.getInstance().registerBean(CoordinatorRepository.class.getName(), repository);//将 JdbcCoordinatorRepository 注入到spring容器中
        });


    }
}
