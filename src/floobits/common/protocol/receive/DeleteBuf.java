package floobits.common.protocol.receive;

import floobits.common.protocol.Base;

public class DeleteBuf extends Base {
    public Integer id;
    public String name = "delete_buf";

    public DeleteBuf(Integer id) {
        this.id = id;
    }
}