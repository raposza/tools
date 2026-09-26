// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import com.raposza.api.PackageCache_i;
import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.ChoiceInfo;
import com.raposza.api.model.DataId;
import com.raposza.api.model.DataShape;
import com.raposza.api.model.InterfaceInfo;
import com.raposza.api.model.PackageShape;
import com.raposza.api.model.TemplateInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A type registry over whatever the package cache holds.
 *
 * It does NOT fetch. Filling the cache is the client's job - it is the only
 * thing that can talk to a participant - and a registry that fetched would
 * have to know about a Ledger API generation, which is exactly the dependency
 * this module does not have.
 *
 * refresh() re-reads the cache and rebuilds the index. A cache hit does not
 * re-decode: the decoded PackageShape is what the cache stores.
 *
 * THE UNION IS COMPLETED HERE. A template's inherited choices come from the
 * interfaces it implements, and an interface can live in a package other than
 * the template's, so decoding a single archive cannot finish the job. The
 * index therefore re-runs ChoiceUnion once every cached package is in hand.
 * That is idempotent - de-duplication is by choice name - so a template whose
 * interfaces were all in its own package is unchanged by the second pass.
 *
 * An interface that is still not cached is skipped rather than failing. The
 * choice list is then incomplete, which is the truth about a participant whose
 * packages have not all been read yet.
 *
 * Author Claude/bentzn
 */
public final class LfTypeRegistry implements TypeRegistry_i {

    private final PackageCache_i cache;

    private final Map<DataId, TemplateInfo> mapTemplate = new LinkedHashMap<>();
    private final Map<DataId, DataShape> mapShape = new LinkedHashMap<>();
    private final Map<DataId, List<ChoiceInfo>> mapChoiceByInterface = new LinkedHashMap<>();

    private boolean flagLoaded;


    /**
     * @param cache the package cache to read
     */
    public LfTypeRegistry(PackageCache_i cache) {
        if (cache == null)
            throw new IllegalArgumentException("package cache is required");

        this.cache = cache;
    }


    /**
     * Read straight off the cached {@link PackageShape} rather than through the
     * index: the index is keyed by template and this question is about a
     * package, including packages holding no template at all.
     */
    @Override
    public Optional<String> namePackage(String idPackage) {
        if (idPackage == null || idPackage.isBlank())
            return Optional.empty();

        Optional<PackageShape> optShape = cache.get(idPackage);
        if (optShape.isEmpty())
            return Optional.empty();

        String nameOut = optShape.get().namePackage();
        return nameOut == null || nameOut.isBlank() ? Optional.empty() : Optional.of(nameOut);
    }


    @Override
    public List<TemplateInfo> templates() {
        load();
        List<TemplateInfo> lstTemplate = new ArrayList<>(mapTemplate.values());
        lstTemplate.sort(Comparator.comparing(TemplateInfo::idTemplate,
                Comparator.comparing(DataId::nameModule).thenComparing(DataId::nameEntity)
                        .thenComparing(DataId::idPackage)));
        return List.copyOf(lstTemplate);
    }


    @Override
    public Optional<TemplateInfo> template(DataId idTemplate) {
        load();
        if (idTemplate == null)
            return Optional.empty();

        return Optional.ofNullable(mapTemplate.get(idTemplate));
    }


    @Override
    public List<TemplateInfo> templatesByName(String nameShort) {
        load();
        if (nameShort == null || nameShort.isBlank())
            return List.of();

        String nameWanted = nameShort.trim();
        List<TemplateInfo> lstMatch = new ArrayList<>();
        for (TemplateInfo template : templates()) {
            DataId idTemplate = template.idTemplate();
            if (nameWanted.equals(idTemplate.shortName())
                    || nameWanted.equals(idTemplate.nameEntity())) {
                lstMatch.add(template);
            }
        }
        return List.copyOf(lstMatch);
    }


    @Override
    public Optional<DataShape> shape(DataId idData) {
        load();
        if (idData == null)
            return Optional.empty();

        return Optional.ofNullable(mapShape.get(idData));
    }


    @Override
    public void refresh() {
        flagLoaded = false;
        load();
    }


    /**
     * Builds the index from the cache, once, and completes the choice union
     * across packages.
     */
    private void load() {
        if (flagLoaded)
            return;

        mapTemplate.clear();
        mapShape.clear();
        mapChoiceByInterface.clear();

        List<PackageShape> lstPackage = new ArrayList<>();
        for (String idPackage : cache.ids()) {
            Optional<PackageShape> optShape = cache.get(idPackage);
            if (optShape.isEmpty())
                continue;

            PackageShape shape = optShape.get();
            lstPackage.add(shape);
            for (InterfaceInfo iface : shape.lstInterface()) {
                mapChoiceByInterface.put(iface.idInterface(), iface.lstChoice());
            }
            for (DataShape dataShape : shape.lstShape()) {
                mapShape.put(dataShape.idData(), dataShape);
            }
        }

        for (PackageShape shape : lstPackage) {
            for (TemplateInfo template : shape.lstTemplate()) {
                List<ChoiceInfo> lstChoice = ChoiceUnion.merge(template.lstChoice(),
                        template.lstInterface(), mapChoiceByInterface);
                mapTemplate.put(template.idTemplate(),
                        new TemplateInfo(template.idTemplate(), template.lstField(), lstChoice,
                                template.lstInterface(), template.typeKey()));
            }
        }
        flagLoaded = true;
    }

}
