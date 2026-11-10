package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LtvPolicyTestHelper {

    public static Map<String, LtvPolicyEntity> createDefaultEntityMap() {
        List<LtvPolicyEntity> defaultPolicies = LtvPolicyDataInitializer.createDefaultPolicies();
        Map<String, LtvPolicyEntity> map = new HashMap<>();
        for (LtvPolicyEntity policy : defaultPolicies) {
            map.put(key(policy.getOccupationGroup(), policy.getCollateralCategory()), policy);
        }
        return map;
    }

    public static String key(CustomerOccupationGroup group, CollateralCategory category) {
        return (group != null ? group.name() : "NULL") + ":" + (category != null ? category.name() : "NULL");
    }

    public static LtvPolicyRepository createMockRepository() {
        Map<String, LtvPolicyEntity> map = createDefaultEntityMap();
        return createMockRepository(map);
    }

    public static LtvPolicyRepository createMockRepository(Map<String, LtvPolicyEntity> map) {
        LtvPolicyRepository repo = mock(LtvPolicyRepository.class);
        org.mockito.Mockito.lenient().when(repo.findByOccupationGroupAndCollateralCategoryAndDeletedFalse(any(), any()))
                .thenAnswer(invocation -> {
                    CustomerOccupationGroup group = invocation.getArgument(0);
                    CollateralCategory category = invocation.getArgument(1);
                    LtvPolicyEntity entity = map.get(key(group, category));
                    if (entity != null && entity.isDeleted()) {
                        return Optional.empty();
                    }
                    return Optional.ofNullable(entity);
                });
        org.mockito.Mockito.lenient().when(repo.findPolicy(any(), any()))
                .thenAnswer(invocation -> {
                    CustomerOccupationGroup group = invocation.getArgument(0);
                    CollateralCategory category = invocation.getArgument(1);
                    LtvPolicyEntity entity = map.get(key(group, category));
                    if (entity != null && entity.isDeleted()) {
                        return Optional.empty();
                    }
                    return Optional.ofNullable(entity);
                });
        return repo;
    }

    public static LtvPolicyService createDefaultPolicyService() {
        return new LtvPolicyServiceImpl(createMockRepository(), new VerificationDocumentPolicy());
    }
}

