/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.record;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.github.greenfinger.core.model.Image;
import com.github.greenfinger.core.model.ResourceImage;

/**
 * Writes the shared image rows, each in a transaction of its own. Two pages can reference one
 * picture at the same moment, and since the id comes from the bytes both threads insert the same
 * row; checking first finds nothing, because the other transaction has not committed.
 *
 * <p>
 * So the insert gets its own transaction and the retry another -- PostgreSQL aborts a transaction
 * on a constraint violation and refuses every later statement in it -- both opened through a
 * template, since a self-call bypasses the annotation proxy. SQLite instead joins the page's
 * transaction, which would otherwise fail with {@code SQLITE_BUSY_SNAPSHOT}.
 * 
 * @Description: ImageWriter
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public class ImageWriter {

    private final ImageRepository imageRepository;
    private final ResourceImageRepository resourceImageRepository;
    private final TransactionTemplate transactions;

    public ImageWriter(ImageRepository imageRepository,
            ResourceImageRepository resourceImageRepository,
            PlatformTransactionManager transactionManager) {
        this(imageRepository, resourceImageRepository, transactionManager, true);
    }

    /**
     * @param ownTransactions whether the image rows get transactions of their own. True everywhere
     *        but SQLite -- see the class comment.
     */
    public ImageWriter(ImageRepository imageRepository,
            ResourceImageRepository resourceImageRepository,
            PlatformTransactionManager transactionManager, boolean ownTransactions) {
        this.imageRepository = imageRepository;
        this.resourceImageRepository = resourceImageRepository;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(ownTransactions
                ? TransactionDefinition.PROPAGATION_REQUIRES_NEW
                : TransactionDefinition.PROPAGATION_REQUIRED);
    }

    public Image findOrCreate(String id, Supplier<Image> factory) {
        Optional<Image> existing = inNewTransaction(() -> imageRepository.findById(id));
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return inNewTransaction(() -> imageRepository.saveAndFlush(factory.get()));
        } catch (DataIntegrityViolationException e) {
            // somebody else inserted the identical bytes; read what they wrote, in a clean
            // transaction, because the failed one is no longer usable
            return inNewTransaction(() -> imageRepository.findById(id)).orElseThrow(() -> e);
        }
    }

    public ResourceImage findOrCreateReference(String id, Supplier<ResourceImage> factory) {
        Optional<ResourceImage> existing =
                inNewTransaction(() -> resourceImageRepository.findById(id));
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return inNewTransaction(() -> resourceImageRepository.saveAndFlush(factory.get()));
        } catch (DataIntegrityViolationException e) {
            return inNewTransaction(() -> resourceImageRepository.findById(id))
                    .orElseThrow(() -> e);
        }
    }

    private <T> T inNewTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

}
