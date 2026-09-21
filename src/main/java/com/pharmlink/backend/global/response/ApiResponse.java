@Getter
@Builder
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private Meta meta;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .meta(Meta.now())
                .build();
    }
}