import React from 'react'

const AddContent = ({submit, expand, comment, update, reply, expanded}) => <form onSubmit={expanded ? submit : expand}
                                                                                 className='card-body'>
  {!!expanded ? <div className='form-group row'>
    <div className='col-sm-3'>
      <label htmlFor="poster">Name: </label>
    </div>
    <div className='col-sm-9'>
      <input type='text' className='form-control' value={comment.poster} onChange={update} name='poster'/>
    </div>
  </div> : null}
  {!!expanded ? <div className='form-group row'>
    <div className='col-sm-3'>
      <label htmlFor="comment">Comment: </label>
    </div>

    <div className='col-sm-9'>
      <textarea className='form-control' value={comment.content} onChange={update} name='content'/>
    </div>
  </div> : null}
  <div className='row'>
    <div className='col-sm-12'>
      <input type='submit' value={reply ? 'Post Reply' : 'Post Comment'} className={`btn ${reply ? 'btn-success' : 'btn-primary'} float-right`}/>
    </div>
  </div>
</form>


export default AddContent