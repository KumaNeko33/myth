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
package com.github.myth.common.enums;


import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author xiaoyu
 */

public enum SerializeEnum {

    /**
     * Jdk serialize protocol enum.
     */
    JDK("jdk"),

    /**
     * Kryo serialize protocol enum.
     */
    KRYO("kryo"),

    /**
     * Hessian serialize protocol enum.
     */
    HESSIAN("hessian"),

    /**
     * Protostuff serialize protocol enum.
     */
    PROTOSTUFF("protostuff");

    private String serialize;

    SerializeEnum(String serialize) {
        this.serialize = serialize;
    }

    /**
     * Acquire serialize protocol serialize protocol enum.
     *
     * @param serialize the serialize protocol
     * @return the serialize protocol enum
     */
    public static SerializeEnum acquire(String serialize) {
//        数组
//        通过Arrays类提供的静态函数stream()获取数组的流对象：
//        String[] names = {"chaimm","peter","john"};
//        Stream<String> stream = Arrays.stream(names);
        Optional<SerializeEnum> serializeEnum =
                Arrays.stream(SerializeEnum.values())//SerializeEnum.values()是所有枚举值的 数组
                        .filter(v -> Objects.equals(v.getSerialize(), serialize)) //将数组转换成流 并过滤出 与传入serialize相等的元素即SerializeEnum
                        .findFirst();//返回元素的Optional对象
        return serializeEnum.orElse(SerializeEnum.KRYO);

    }

    /**
     * Gets serialize protocol.
     *
     * @return the serialize protocol
     */
    public String getSerialize() {
        return serialize;
    }

    /**
     * Sets serialize protocol.
     *
     * @param serialize the serialize protocol
     */
    public void setSerialize(String serialize) {
        this.serialize = serialize;
    }


}
