package io.micronaut.docs.ioc.mappers;

import io.micronaut.context.annotation.Mapper;
import io.micronaut.core.annotation.Introspected;

public interface PrimitiveTypes {

    @Introspected
    public static class SourceWithPrimitive {
        private long id;
        private int count;
        private boolean active;
        private float score;
        private double value;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public boolean getActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
    }

    @Introspected
    public static class TargetWithWrapper {
        private Long id;
        private Integer count;
        private Boolean active;
        private Float score;
        private Double value;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
        public Float getScore() { return score; }
        public void setScore(Float score) { this.score = score; }
        public Double getValue() { return value; }
        public void setValue(Double value) { this.value = value; }
    }

    @Introspected
    public static class SourceWithWrapper {
        private Long id;
        private Integer count;
        private Boolean active;
        private Float score;
        private Double value;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
        public Float getScore() { return score; }
        public void setScore(Float score) { this.score = score; }
        public Double getValue() { return value; }
        public void setValue(Double value) { this.value = value; }
    }

    @Introspected
    public static class TargetWithPrimitive {
        private long id;
        private int count;
        private boolean active;
        private float score;
        private double value;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public boolean getActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
    }

    public interface PrimitiveMapper {
        @Mapper.Mapping(from = "id", to = "id")
        @Mapper.Mapping(from = "count", to = "count")
        @Mapper.Mapping(from = "active", to = "active")
        @Mapper.Mapping(from = "score", to = "score")
        @Mapper.Mapping(from = "value", to = "value")
        TargetWithWrapper convert(SourceWithPrimitive source);

        @Mapper.Mapping(from = "id", to = "id")
        @Mapper.Mapping(from = "count", to = "count")
        @Mapper.Mapping(from = "active", to = "active")
        @Mapper.Mapping(from = "score", to = "score")
        @Mapper.Mapping(from = "value", to = "value")
        TargetWithPrimitive convertToPrimitive(SourceWithWrapper source);
    }

}
