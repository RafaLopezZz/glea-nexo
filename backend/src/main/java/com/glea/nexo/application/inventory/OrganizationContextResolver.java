package com.glea.nexo.application.inventory;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.glea.nexo.domain.location.Organization;
import com.glea.nexo.domain.repository.OrganizationRepository;

import jakarta.persistence.EntityNotFoundException;

@Component
public class OrganizationContextResolver {

    public static final String ORG_HEADER = "X-Org-Code";
    private static final String DEFAULT_ORG_CODE = "default";

    private final OrganizationRepository organizationRepository;

    public OrganizationContextResolver(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public Organization resolveCurrentOrganization() {
        String orgCode = resolveOrganizationCode();
        return organizationRepository.findByCode(orgCode)
                .orElseThrow(() -> new EntityNotFoundException("Organization with code '%s' not found".formatted(orgCode)));
    }

    private String resolveOrganizationCode() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return DEFAULT_ORG_CODE;
        }

        String headerValue = attributes.getRequest().getHeader(ORG_HEADER);
        if (!StringUtils.hasText(headerValue)) {
            return DEFAULT_ORG_CODE;
        }

        return headerValue.trim();
    }
}
