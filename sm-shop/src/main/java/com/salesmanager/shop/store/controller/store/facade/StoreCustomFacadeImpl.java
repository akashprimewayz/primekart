package com.salesmanager.shop.store.controller.store.facade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Validate;
import org.drools.core.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.salesmanager.core.business.exception.ConversionException;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.content.ContentService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.constants.MeasureUnit;
import com.salesmanager.core.model.common.GenericEntityList;
import com.salesmanager.core.model.content.InputContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.merchant.MerchantStoreCriteria;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;
import com.salesmanager.shop.model.content.ReadableImage;
import com.salesmanager.shop.model.store.MerchantConfigEntity;
import com.salesmanager.shop.model.store.PersistableBrand;
import com.salesmanager.shop.model.store.PersistableCustomMerchantStore;
import com.salesmanager.shop.model.store.ReadableBrand;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.store.ReadableMerchantStoreList;
import com.salesmanager.shop.populator.store.PersistableCustomMerchantStorePopulator;
import com.salesmanager.shop.populator.store.ReadableMerchantStorePopulator;
import com.salesmanager.shop.store.api.exception.ConversionRuntimeException;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.utils.ImageFilePath;
import com.salesmanager.shop.utils.LanguageUtils;

@Service("storeCustomFacade")
public class StoreCustomFacadeImpl implements StoreCustomFacade {

	@Inject
	private MerchantStoreService merchantStoreService;

	@Inject
	private MerchantConfigurationService merchantConfigurationService;

	@Inject
	private LanguageService languageService;

	@Inject
	private ContentService contentService;

	@Inject
	private PersistableCustomMerchantStorePopulator persistableCustomMerchantStorePopulator;

	@Inject
	@Qualifier("img")
	private ImageFilePath imageUtils;

	@Inject
	private LanguageUtils languageUtils;

	@Autowired
	private ReadableMerchantStorePopulator readableMerchantStorePopulator;

	private static final Logger LOG = LoggerFactory.getLogger(StoreCustomFacadeImpl.class);

	@Override
	public MerchantStore getByCode(HttpServletRequest request) {
		String code = request.getParameter("store");
		if (StringUtils.isEmpty(code)) {
			code = com.salesmanager.core.business.constants.Constants.DEFAULT_STORE;
		}
		return get(code);
	}

	@Override
	public MerchantStore get(String code) {
		try {
			final MerchantStore store = merchantStoreService.getByCode(code);
			return store;
		} catch (final ServiceException e) {
			LOG.error("Error while getting MerchantStore", e);
			throw new ServiceRuntimeException(e);
		}

	}

	@Override
	public ReadableMerchantStore getByCode(String code, String lang) {
		final Language language = getLanguage(lang);
		return getByCode(code, language);
	}

	@Override
	public ReadableMerchantStore getFullByCode(String code, String lang) {
		final Language language = getLanguage(lang);
		return getFullByCode(code, language);
	}

	private Language getLanguage(String lang) {
		return languageUtils.getServiceLanguage(lang);
	}

	@Override
	public ReadableMerchantStore getByCode(String code, Language language) {
		final MerchantStore store = getMerchantStoreByCode(code);
		return convertMerchantStoreToReadableMerchantStore(language, store);
	}

	@Override
	public ReadableMerchantStore getFullByCode(String code, Language language) {
		final MerchantStore store = getMerchantStoreByCode(code);
		return convertMerchantStoreToReadableMerchantStoreWithFullDetails(language, store);
	}

	@Override
	public boolean existByCode(String code) {
		try {
			return merchantStoreService.getByCode(code) != null;
		} catch (final ServiceException e) {
			throw new ServiceRuntimeException(e);
		}
	}

	private ReadableMerchantStore convertMerchantStoreToReadableMerchantStore(Language language, MerchantStore store) {
		final ReadableMerchantStore readable = new ReadableMerchantStore();

		/**
		 * Language is not important for this conversion using default language
		 */
		try {
			readableMerchantStorePopulator.populate(store, readable, store, language);
		} catch (final Exception e) {
			throw new ConversionRuntimeException("Error while populating MerchantStore " + e.getMessage());
		}
		return readable;
	}

	private ReadableMerchantStore convertMerchantStoreToReadableMerchantStoreWithFullDetails(Language language,
			MerchantStore store) {
		final ReadableMerchantStore readable = new ReadableMerchantStore();

		/**
		 * Language is not important for this conversion using default language
		 */
		try {
			readableMerchantStorePopulator.populate(store, readable, store, language);
		} catch (final Exception e) {
			throw new ConversionRuntimeException("Error while populating MerchantStore " + e.getMessage());
		}
		return readable;
	}

	private MerchantStore getMerchantStoreByCode(String code) {
		return Optional.ofNullable(get(code))
				.orElseThrow(() -> new ResourceNotFoundException("Merchant store code [" + code + "] not found"));
	}

	@Override
	public void create(PersistableCustomMerchantStore store) {

		Validate.notNull(store, "PersistableMerchantStore must not be null");
		Validate.notNull(store.getCode(), "PersistableMerchantStore.code must not be null");

		// check if store code exists
		final MerchantStore storeForCheck = get(store.getCode());
		if (storeForCheck != null) {
			throw new ServiceRuntimeException("MerhantStore " + store.getCode() + " already exists");
		}

		final MerchantStore mStore = convertPersistableMerchantStoreToMerchantStore(store,
				languageService.defaultLanguage());
		createMerchantStore(mStore);

	}

	private void createMerchantStore(MerchantStore mStore) {
		try {
			merchantStoreService.saveOrUpdate(mStore);
		} catch (final ServiceException e) {
			throw new ServiceRuntimeException(e);
		}
	}

	private MerchantStore convertPersistableMerchantStoreToMerchantStore(PersistableCustomMerchantStore store,
			Language language) {
		MerchantStore mStore = new MerchantStore();

		// set default values
		mStore.setWeightunitcode(MeasureUnit.KG.name());
		mStore.setSeizeunitcode(MeasureUnit.IN.name());

		try {
			mStore = persistableCustomMerchantStorePopulator.populate(store, mStore, language);
		} catch (final ConversionException e) {
			throw new ConversionRuntimeException(e);
		}
		return mStore;
	}

	@Override
	public void update(PersistableCustomMerchantStore store) {

		Validate.notNull(store);

		final MerchantStore mStore = mergePersistableMerchantStoreToMerchantStore(store, store.getCode(),
				languageService.defaultLanguage());

		updateMerchantStore(mStore);

	}

	private void updateMerchantStore(MerchantStore mStore) {
		try {
			merchantStoreService.update(mStore);
		} catch (final ServiceException e) {
			throw new ServiceRuntimeException(e);
		}

	}

	private MerchantStore mergePersistableMerchantStoreToMerchantStore(PersistableCustomMerchantStore store,
			String code, Language language) {

		MerchantStore mStore = getMerchantStoreByCode(code);

		store.setId(mStore.getId());

		try {
			mStore = persistableCustomMerchantStorePopulator.populate(store, mStore, language);
		} catch (final ConversionException e) {
			throw new ConversionRuntimeException(e);
		}
		return mStore;
	}

	@Override
	public ReadableMerchantStoreList getByCriteria(MerchantStoreCriteria criteria, Language lang) {
		return getMerchantStoresByCriteria(criteria, lang);

	}

	private ReadableMerchantStoreList getMerchantStoresByCriteria(MerchantStoreCriteria criteria, Language language) {
		try {
			final GenericEntityList<MerchantStore> stores = Optional
					.ofNullable(merchantStoreService.getByCriteria(criteria))
					.orElseThrow(() -> new ResourceNotFoundException("Criteria did not match any store"));

			final ReadableMerchantStoreList storeList = new ReadableMerchantStoreList();
			storeList.setData(stores.getList().stream()
					.map(s -> convertMerchantStoreToReadableMerchantStore(language, s)).collect(Collectors.toList()));
			storeList.setTotalPages(stores.getTotalPages());
			storeList.setRecordsTotal(stores.getTotalCount());
			storeList.setNumber(stores.getList().size());

			return storeList;

		} catch (final ServiceException e) {
			throw new ServiceRuntimeException(e);
		}

	}

	@Override
	public void delete(String code) {

		if (MerchantStore.DEFAULT_STORE.equals(code.toUpperCase())) {
			throw new ServiceRuntimeException("Cannot remove default store");
		}

		final MerchantStore mStore = getMerchantStoreByCode(code);

		try {
			merchantStoreService.delete(mStore);
		} catch (final Exception e) {
			LOG.error("Error while deleting MerchantStore", e);
			throw new ServiceRuntimeException("Error while deleting MerchantStore " + e.getMessage());
		}

	}

	@Override
	public ReadableBrand getBrand(String code) {
		final MerchantStore mStore = getMerchantStoreByCode(code);

		final ReadableBrand readableBrand = new ReadableBrand();
		if (!StringUtils.isEmpty(mStore.getStoreLogo())) {
			final String imagePath = imageUtils.buildStoreLogoFilePath(mStore);
			final ReadableImage image = createReadableImage(mStore.getStoreLogo(), imagePath);
			readableBrand.setLogo(image);
		}
		final List<MerchantConfigEntity> merchantConfigTOs = getMerchantConfigEntities(mStore);
		readableBrand.getSocialNetworks().addAll(merchantConfigTOs);
		return readableBrand;
	}

	private List<MerchantConfigEntity> getMerchantConfigEntities(MerchantStore mStore) {
		final List<MerchantConfiguration> configurations = getMergeConfigurationsByStore(
				MerchantConfigurationType.SOCIAL, mStore);

		return configurations.stream().map(this::convertToMerchantConfigEntity).collect(Collectors.toList());
	}

	private List<MerchantConfiguration> getMergeConfigurationsByStore(MerchantConfigurationType configurationType,
			MerchantStore mStore) {
		try {
			return merchantConfigurationService.listByType(configurationType, mStore);
		} catch (final ServiceException e) {
			throw new ServiceRuntimeException("Error wile getting merchantConfigurations " + e.getMessage());
		}
	}

	private MerchantConfigEntity convertToMerchantConfigEntity(MerchantConfiguration config) {
		final MerchantConfigEntity configTO = new MerchantConfigEntity();
		configTO.setId(config.getId());
		configTO.setKey(config.getKey());
		configTO.setType(config.getMerchantConfigurationType());
		configTO.setValue(config.getValue());
		configTO.setActive(config.getActive() != null ? config.getActive().booleanValue() : false);
		return configTO;
	}

	private MerchantConfiguration convertToMerchantConfiguration(MerchantConfigEntity config,
			MerchantConfigurationType configurationType) {
		final MerchantConfiguration configTO = new MerchantConfiguration();
		configTO.setId(config.getId());
		configTO.setKey(config.getKey());
		configTO.setMerchantConfigurationType(configurationType);
		configTO.setValue(config.getValue());
		configTO.setActive(new Boolean(config.isActive()));
		return configTO;
	}

	private ReadableImage createReadableImage(String storeLogo, String imagePath) {
		final ReadableImage image = new ReadableImage();
		image.setName(storeLogo);
		image.setPath(imagePath);
		return image;
	}

	@Override
	public void deleteLogo(String code) {
		final MerchantStore store = getByCode(code);
		final String image = store.getStoreLogo();
		store.setStoreLogo(null);

		try {
			updateMerchantStore(store);
			if (!StringUtils.isEmpty(image)) {
				contentService.removeFile(store.getCode(), image);
			}
		} catch (final ServiceException e) {
			throw new ServiceRuntimeException(e.getMessage());
		}
	}

	@Override
	public MerchantStore getByCode(String code) {
		return getMerchantStoreByCode(code);
	}

	@Override
	public void addStoreLogo(String code, InputContentFile cmsContentImage) {
		final MerchantStore store = getByCode(code);
		store.setStoreLogo(cmsContentImage.getFileName());
		saveMerchantStore(store);
		addLogoToStore(code, cmsContentImage);
	}

	private void addLogoToStore(String code, InputContentFile cmsContentImage) {
		try {
			contentService.addLogo(code, cmsContentImage);
		} catch (final ServiceException e) {
			throw new ServiceRuntimeException(e);
		}
	}

	private void saveMerchantStore(MerchantStore store) {
		try {
			merchantStoreService.save(store);
		} catch (final ServiceException e) {
			throw new ServiceRuntimeException(e);
		}

	}

	@Override
	public void createBrand(String merchantStoreCode, PersistableBrand brand) {
		final MerchantStore mStore = getMerchantStoreByCode(merchantStoreCode);

		final List<MerchantConfigEntity> createdConfigs = brand.getSocialNetworks();

		final List<MerchantConfiguration> configurations = createdConfigs.stream()
				.map(config -> convertToMerchantConfiguration(config, MerchantConfigurationType.SOCIAL))
				.collect(Collectors.toList());
		try {
			for (final MerchantConfiguration mConfigs : configurations) {
				mConfigs.setMerchantStore(mStore);
				if (!StringUtils.isEmpty(mConfigs.getValue())) {
					mConfigs.setMerchantConfigurationType(MerchantConfigurationType.SOCIAL);
					merchantConfigurationService.saveOrUpdate(mConfigs);
				} else {// remove if submited blank and exists
					final MerchantConfiguration config = merchantConfigurationService
							.getMerchantConfiguration(mConfigs.getKey(), mStore);
					if (config != null) {
						merchantConfigurationService.delete(config);
					}
				}
			}
		} catch (final ServiceException se) {
			throw new ServiceRuntimeException(se);
		}

	}

	@Override
	public ReadableMerchantStoreList getChildStores(Language language, String code, int page, int count) {
		try {

			// first check if store is retailer
			final MerchantStore retailer = this.getByCode(code);
			if (retailer == null) {
				throw new ResourceNotFoundException("Merchant [" + code + "] not found");
			}

			if (retailer.isRetailer() == null || !retailer.isRetailer().booleanValue()) {
				throw new ResourceNotFoundException("Merchant [" + code + "] not a retailer");
			}

			final Page<MerchantStore> children = merchantStoreService.listChildren(code, page, count);
			final List<ReadableMerchantStore> readableStores = new ArrayList<>();
			final ReadableMerchantStoreList readableList = new ReadableMerchantStoreList();
			if (!CollectionUtils.isEmpty(children.getContent())) {
				for (final MerchantStore store : children) {
					readableStores.add(convertMerchantStoreToReadableMerchantStore(language, store));
				}
			}
			readableList.setData(readableStores);
			readableList.setRecordsFiltered(children.getSize());
			readableList.setTotalPages(children.getTotalPages());
			readableList.setRecordsTotal(children.getTotalElements());
			readableList.setNumber(children.getNumber());

			return readableList;

			/*
			 * List<MerchantStore> children = merchantStoreService.listChildren(code);
			 * List<ReadableMerchantStore> readableStores = new
			 * ArrayList<ReadableMerchantStore>(); if (!CollectionUtils.isEmpty(children)) {
			 * for (MerchantStore store : children)
			 * readableStores.add(convertMerchantStoreToReadableMerchantStore(language,
			 * store)); } return readableStores;
			 */
		} catch (final ServiceException e) {
			throw new ServiceRuntimeException(e);
		}

	}

	@Override
	public ReadableMerchantStoreList findAll(MerchantStoreCriteria criteria, Language language, int page, int count) {

		try {
			Page<MerchantStore> stores = null;
			final List<ReadableMerchantStore> readableStores = new ArrayList<>();
			final ReadableMerchantStoreList readableList = new ReadableMerchantStoreList();

			final Optional<String> code = Optional.ofNullable(criteria.getStoreCode());
			final Optional<String> name = Optional.ofNullable(criteria.getName());
			if (code.isPresent()) {

				stores = merchantStoreService.listByGroup(name, code.get(), page, count);

			} else if (criteria.isRetailers()) {
				stores = merchantStoreService.listAllRetailers(name, page, count);
			} else {
				stores = merchantStoreService.listAll(name, page, count);
			}

			if (!CollectionUtils.isEmpty(stores.getContent())) {
				for (final MerchantStore store : stores) {
					readableStores.add(convertMerchantStoreToReadableMerchantStore(language, store));
				}
			}
			readableList.setData(readableStores);
			readableList.setRecordsTotal(stores.getTotalElements());
			readableList.setTotalPages(stores.getTotalPages());
			readableList.setNumber(stores.getSize());
			readableList.setRecordsFiltered(stores.getSize());
			return readableList;

		} catch (final ServiceException e) {
			throw new ServiceRuntimeException("Error while finding all merchant", e);
		}

	}

	private ReadableMerchantStore convertStoreName(MerchantStore store) {
		final ReadableMerchantStore convert = new ReadableMerchantStore();
		convert.setId(store.getId());
		convert.setCode(store.getCode());
		convert.setName(store.getStorename());
		return convert;
	}

	@Override
	public List<ReadableMerchantStore> getMerchantStoreNames(MerchantStoreCriteria criteria) {
		Validate.notNull(criteria, "MerchantStoreCriteria must not be null");

		try {

			List<ReadableMerchantStore> stores = null;
			final Optional<String> code = Optional.ofNullable(criteria.getStoreCode());

			// TODO Pageable
			if (code.isPresent()) {

				stores = merchantStoreService.findAllStoreNames(code.get()).stream().map(this::convertStoreName)
						.collect(Collectors.toList());
			} else {
				stores = merchantStoreService.findAllStoreNames().stream().map(this::convertStoreName)
						.collect(Collectors.toList());
			}

			return stores;
		} catch (final ServiceException e) {
			throw new ServiceRuntimeException("Exception while getting store name", e);
		}

	}

	@Override
	public List<Language> supportedLanguages(MerchantStore store) {

		Validate.notNull(store, "MerchantStore cannot be null");
		Validate.notNull(store.getClass(), "MerchantStore code cannot be null");

		if (!CollectionUtils.isEmpty(store.getLanguages())) {
			return store.getLanguages();
		}

		// refresh
		try {
			store = merchantStoreService.getByCode(store.getCode());
		} catch (final ServiceException e) {
			throw new ServiceRuntimeException("An exception occured when getting store [" + store.getCode() + "]");
		}

		if (store != null) {
			return store.getLanguages();
		}

		return Collections.emptyList();
	}

}