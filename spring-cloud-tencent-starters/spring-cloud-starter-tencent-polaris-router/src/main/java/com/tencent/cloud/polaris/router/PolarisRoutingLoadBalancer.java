/*
 * Tencent is pleased to support the open source community by making Spring Cloud Tencent available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.tencent.cloud.polaris.router;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.PollingServerListUpdater;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.tencent.cloud.metadata.constant.MetadataConstant.SystemMetadataKey;
import com.tencent.cloud.metadata.context.MetadataContextHolder;
import com.tencent.cloud.polaris.pojo.PolarisServer;
import com.tencent.polaris.api.pojo.Instance;
import com.tencent.polaris.api.pojo.ServiceInfo;
import com.tencent.polaris.api.pojo.ServiceInstances;
import com.tencent.polaris.router.api.core.RouterAPI;
import com.tencent.polaris.router.api.rpc.ProcessRoutersRequest;
import com.tencent.polaris.router.api.rpc.ProcessRoutersResponse;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Haotian Zhang
 */
public class PolarisRoutingLoadBalancer extends DynamicServerListLoadBalancer<Server> {

    private final RouterAPI routerAPI;

    public PolarisRoutingLoadBalancer(IClientConfig config, IRule rule, IPing ping,
            ServerList<Server> serverList, RouterAPI routerAPI) {
        super(config, rule, ping, serverList, null, new PollingServerListUpdater());
        this.routerAPI = routerAPI;
    }

    @Override
    public List<Server> getReachableServers() {
        List<Server> allServers = super.getAllServers();
        if (CollectionUtils.isEmpty(allServers)) {
            return allServers;
        }
        ServiceInstances serviceInstances = null;
        if (allServers.get(0) instanceof PolarisServer) {
            serviceInstances = ((PolarisServer) allServers.get(0)).getServiceInstances();
        } else {
            // TODO serviceInstances = DefaultServiceInstancesImpl.createByServerList(allServers);
            throw new IllegalStateException("PolarisRoutingLoadBalancer only support PolarisServer instances");
        }
        ProcessRoutersRequest processRoutersRequest = new ProcessRoutersRequest();
        processRoutersRequest.setDstInstances(serviceInstances);
        String srcNamespace = MetadataContextHolder.get().getSystemMetadata(SystemMetadataKey.LOCAL_NAMESPACE);
        String srcService = MetadataContextHolder.get().getSystemMetadata(SystemMetadataKey.LOCAL_SERVICE);
        Map<String, String> transitiveCustomMetadata = MetadataContextHolder.get().getAllTransitiveCustomMetadata();
        String method = MetadataContextHolder.get().getSystemMetadata(SystemMetadataKey.PEER_PATH);
        processRoutersRequest.setMethod(method);
        if (StringUtils.isNotBlank(srcNamespace) && StringUtils.isNotBlank(srcService)) {
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setNamespace(srcNamespace);
            serviceInfo.setService(srcService);
            serviceInfo.setMetadata(transitiveCustomMetadata);
            processRoutersRequest.setSourceService(serviceInfo);
        }
        ProcessRoutersResponse processRoutersResponse = routerAPI.processRouters(processRoutersRequest);
        ServiceInstances filteredInstances = processRoutersResponse.getServiceInstances();
        List<Server> instances = new ArrayList<>();
        for (Instance instance : filteredInstances.getInstances()) {
            instances.add(new PolarisServer(serviceInstances, instance));
        }
        return instances;
    }

    @Override
    public List<Server> getAllServers() {
        return getReachableServers();
    }
}