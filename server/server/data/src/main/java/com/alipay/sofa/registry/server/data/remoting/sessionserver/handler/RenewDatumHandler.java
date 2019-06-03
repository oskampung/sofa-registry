/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.registry.server.data.remoting.sessionserver.handler;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.alipay.sofa.registry.common.model.CommonResponse;
import com.alipay.sofa.registry.common.model.GenericResponse;
import com.alipay.sofa.registry.common.model.Node;
import com.alipay.sofa.registry.common.model.PublisherDigestUtil;
import com.alipay.sofa.registry.common.model.ReNewDatumRequest;
import com.alipay.sofa.registry.common.model.dataserver.ClientOffRequest;
import com.alipay.sofa.registry.common.model.store.Publisher;
import com.alipay.sofa.registry.log.Logger;
import com.alipay.sofa.registry.log.LoggerFactory;
import com.alipay.sofa.registry.remoting.Channel;
import com.alipay.sofa.registry.server.data.cache.DatumCache;
import com.alipay.sofa.registry.server.data.remoting.handler.AbstractServerHandler;
import com.alipay.sofa.registry.server.data.remoting.sessionserver.forward.ForwardService;
import com.alipay.sofa.registry.util.ParaCheckUtil;

/**
 * handling snapshot request
 *
 * @author kezhu.wukz
 * @version $Id: ClientOffProcessor.java, v 0.1 2019-05-30 15:48 kezhu.wukz Exp $
 */
public class RenewDatumHandler extends AbstractServerHandler<ReNewDatumRequest> {

    /** LOGGER */
    private static final Logger LOGGER = LoggerFactory.getLogger(RenewDatumHandler.class);

    @Autowired
    private ForwardService      forwardService;

    @Override
    public void checkParam(ReNewDatumRequest request) throws RuntimeException {
        ParaCheckUtil.checkNotBlank(request.getConnectId(), "ReNewDatumRequest.connectId");
        ParaCheckUtil.checkNotBlank(request.getDigestSum(), "ReNewDatumRequest.digestSum");
    }

    @Override
    public Object doHandle(Channel channel, ReNewDatumRequest request) {
        if (forwardService.needForward()) {
            LOGGER.warn("[forward] Renew request refused, request: {}", request);
            CommonResponse response = new CommonResponse();
            response.setSuccess(false);
            response.setMessage("Renew request refused, Server status is not working");
            return response;
        }

        boolean isDiff = renewDatum(request);

        return new GenericResponse<Boolean>().fillSucceed(isDiff);
    }

    @Override
    public CommonResponse buildFailedResponse(String msg) {
        return CommonResponse.buildFailedResponse(msg);
    }

    @Override
    public HandlerType getType() {
        return HandlerType.PROCESSER;
    }

    @Override
    public Class interest() {
        return ClientOffRequest.class;
    }

    @Override
    protected Node.NodeType getConnectNodeType() {
        return Node.NodeType.DATA;
    }

    /**
     * 1. Update the timestamp corresponding to connectId in DatumCache
     * 2. Compare checksum: Get all pubs corresponding to the connId from DatumCache and calculate checksum.
     */
    private boolean renewDatum(ReNewDatumRequest request) {
        String connectId = request.getConnectId();
        DatumCache.renew(connectId);
        Map<String, Publisher> publisherMap = DatumCache.getByConnectId(connectId);

        String renewDigest = request.getDigestSum();
        String cacheDigest = null;
        if (publisherMap != null && publisherMap.values().size() > 0) {
            cacheDigest = String.valueOf(PublisherDigestUtil.getDigestValueSum(publisherMap
                .values()));
        }

        return StringUtils.equals(renewDigest, cacheDigest);
    }
}
