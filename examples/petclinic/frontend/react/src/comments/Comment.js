import React from 'react'


const Comment = ({comment, isThread, expand, close, expanded}) => <div className="card-body">
    <div className='row'>
      <div className='col-sm-10'>
        <h6 className="card-title"><b>{comment.poster}</b> said:</h6>
        <p className="card-text">{comment.content}</p>
      </div>
      <div className='col-sm-2'>
        {isThread && !expanded ? <button className='btn btn-success' onClick={expand} style={{float: 'right'}}>+</button> :
          isThread && expanded ? <button className='btn btn-danger' onClick={close} style={{float: 'right'}}>-</button> : null}
      </div>

    </div>


  </div>


export default Comment;