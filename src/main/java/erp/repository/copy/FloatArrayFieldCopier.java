package erp.repository.copy;

import java.lang.reflect.Field;

import erp.util.Unsafe;

public class FloatArrayFieldCopier extends BaseFieldCopier {

    public FloatArrayFieldCopier(Field field) {
        super(field);
    }

    @Override
    public void copyField(Object fromEntity, Object toEntity) {
        float[] array = (float[]) Unsafe.getObjectFieldOfObject(fromEntity, fieldOffset);
        Unsafe.setObjectFieldOfObject(toEntity, fieldOffset, array.clone());
    }

}
