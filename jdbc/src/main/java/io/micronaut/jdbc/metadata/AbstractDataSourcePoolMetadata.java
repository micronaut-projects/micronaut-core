package io.micronaut.jdbc.metadata;

import javax.sql.DataSource;

/**
 * A base {@link DataSourcePoolMetadata} implementation.
 *
 * @param <T> the data source type
 * @author Stephane Nicoll
 * @author Christian Oestreich
 * @since 2.0.0
 */
public abstract class AbstractDataSourcePoolMetadata<T extends DataSource> implements DataSourcePoolMetadata {

	private final T dataSource;
	private final String name;

	/**
	 * Create an instance with the data source to use.
	 * @param dataSource the data source
	 */
	protected AbstractDataSourcePoolMetadata(T dataSource, String name) {
		this.dataSource = dataSource;
		this.name = name;
	}

	protected final String getName() {
		return this.name;
	}

	@Override
	public Float getUsage() {
		Integer maxSize = getMax();
		Integer currentSize = getActive();
		if (maxSize == null || currentSize == null) {
			return null;
		}
		if (maxSize < 0) {
			return -1F;
		}
		if (currentSize == 0) {
			return 0F;
		}
		return (float) currentSize / (float) maxSize;
	}

	protected final T getDataSource() {
		return this.dataSource;
	}

}